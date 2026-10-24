package com.videocloud.video;

import com.videocloud.api.CurrentWorkspace;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Local-disk resumable upload API. Chunk PUTs are idempotent when the same
 * index and checksum are retried. Clients must upload indexes sequentially or
 * concurrently, then call complete; no chunk is trusted without SHA-256.
 */
@RestController
@RequestMapping("/api/v1/uploads/resumable")
public class ResumableUploadController {
    private static final long CHUNK_BYTES = 8L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("video/mp4","video/quicktime","video/webm","video/x-matroska");
    private static final Pattern SHA = Pattern.compile("^[a-fA-F0-9]{64}$");
    private final JdbcTemplate jdbc;
    private final CurrentWorkspace workspaces;
    private final WorkspaceQuotaService quotas;
    private final VideoProcessingService processor;
    private final Path root;
    private final long maxUploadBytes;

    public ResumableUploadController(JdbcTemplate jdbc, CurrentWorkspace workspaces, WorkspaceQuotaService quotas,
                                     VideoProcessingService processor,
                                     @Value("${videocloud.media-root:./var/media}") String mediaRoot,
                                     @Value("${videocloud.max-upload-bytes:2147483648}") long maxUploadBytes) {
        this.jdbc=jdbc; this.workspaces=workspaces; this.quotas=quotas; this.processor=processor;
        this.root=Path.of(mediaRoot).toAbsolutePath().normalize(); this.maxUploadBytes=maxUploadBytes;
    }

    public record StartRequest(String title, long sizeBytes, String contentType, String filename, String sha256) {}
    public record UploadCreated(UUID uploadId, UUID videoId, long chunkSizeBytes, int chunkCount, Instant expiresAt) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UploadCreated start(@RequestBody StartRequest request, Authentication auth) {
        if (request.title()==null || request.title().isBlank() || request.title().trim().length()>250)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Title is required (max 250 characters)");
        if (request.sizeBytes()<=0 || request.sizeBytes()>maxUploadBytes)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Invalid or excessive upload size");
        if (request.contentType()==null || !TYPES.contains(request.contentType()))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Unsupported video content type");
        String ext=extension(request.filename());
        UUID workspaceId=workspaces.id(auth), userId=workspaces.userId(auth);
        workspaces.requireEditor(auth, workspaceId);
        quotas.assertCanStartUpload(workspaceId,request.sizeBytes());
        UUID videoId=UUID.randomUUID(), uploadId=UUID.randomUUID();
        Path sessionDir=sessionDir(workspaceId,uploadId);
        try { Files.createDirectories(sessionDir); }
        catch(IOException e){ throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Could not create upload workspace"); }
        Path assembled=root.resolve("workspaces").resolve(workspaceId.toString()).resolve("videos").resolve(videoId.toString()).resolve("source."+ext).normalize();
        if(!assembled.startsWith(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid upload path");
        String key=root.relativize(assembled).toString();
        jdbc.update("INSERT INTO videos(id,workspace_id,title,visibility,status,source_object_key,created_by) VALUES (?,?,?,'PRIVATE','UPLOADING',?,?)",
            videoId,workspaceId,request.title().trim(),key,userId);
        jdbc.update("INSERT INTO upload_sessions(id,video_id,object_key,expected_size_bytes,content_type,status,expires_at,expected_sha256,chunk_size_bytes) VALUES (?,?,?,?,?,'CREATED',now()+interval '24 hours',?,?)",
            uploadId,videoId,key,request.sizeBytes(),request.contentType(),normalizeSha(request.sha256()),CHUNK_BYTES);
        int count=(int)((request.sizeBytes()+CHUNK_BYTES-1)/CHUNK_BYTES);
        return new UploadCreated(uploadId,videoId,CHUNK_BYTES,count,Instant.now().plusSeconds(86400));
    }

    @PutMapping("/{uploadId}/chunks/{index}")
    @Transactional
    public Map<String,Object> putChunk(@PathVariable UUID uploadId,@PathVariable int index,
                                      @RequestHeader("X-Chunk-SHA256") String suppliedSha,
                                      @RequestBody byte[] bytes, Authentication auth) throws IOException {
        UUID workspaceId=workspaces.id(auth);
        workspaces.requireEditor(auth, workspaceId);
        Map<String,Object> session=session(uploadId,workspaceId,true);
        long expected=((Number)session.get("expected_size_bytes")).longValue();
        int count=(int)((expected+CHUNK_BYTES-1)/CHUNK_BYTES);
        if(index<0 || index>=count) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid chunk index");
        long expectedChunk=Math.min(CHUNK_BYTES,expected-(long)index*CHUNK_BYTES);
        if(bytes.length!=expectedChunk) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chunk size does not match expected size");
        if(!SHA.matcher(suppliedSha).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"X-Chunk-SHA256 must be a SHA-256 hex digest");
        String actual=sha256(bytes);
        if(!actual.equalsIgnoreCase(suppliedSha)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chunk checksum mismatch");
        String old=jdbc.query("SELECT sha256 FROM upload_chunks WHERE upload_session_id=? AND chunk_index=?",
            rs->rs.next()?rs.getString(1).trim():null,uploadId,index);
        Path dir=sessionDir(workspaceId,uploadId);Files.createDirectories(dir);
        Path chunk=dir.resolve(String.format(Locale.ROOT,"chunk-%08d.part",index));
        if(old!=null) {
            if(!old.equalsIgnoreCase(actual)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Chunk index already uploaded with different content");
            return Map.of("uploadId",uploadId.toString(),"chunkIndex",index,"sha256",actual,"duplicate",true);
        }
        Path temp=dir.resolve(chunk.getFileName()+".tmp");
        Files.write(temp,bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        try { Files.move(temp,chunk,StandardCopyOption.ATOMIC_MOVE); }
        catch(AtomicMoveNotSupportedException e){ Files.move(temp,chunk); }
        jdbc.update("INSERT INTO upload_chunks(upload_session_id,chunk_index,size_bytes,sha256,object_key) VALUES (?,?,?,?,?)",
            uploadId,index,bytes.length,actual,root.relativize(chunk).toString());
        jdbc.update("UPDATE upload_sessions SET status='UPLOADING',updated_at=now() WHERE id=?",uploadId);
        return Map.of("uploadId",uploadId.toString(),"chunkIndex",index,"sha256",actual,"duplicate",false);
    }

    @PostMapping("/{uploadId}/complete")
    @Transactional
    public Map<String,Object> complete(@PathVariable UUID uploadId, Authentication auth) throws IOException {
        UUID workspaceId=workspaces.id(auth);
        workspaces.requireEditor(auth, workspaceId);
        Map<String,Object> s=session(uploadId,workspaceId,true);
        UUID videoId=(UUID)s.get("video_id");
        long expected=((Number)s.get("expected_size_bytes")).longValue();
        long actualSize=Optional.ofNullable(jdbc.queryForObject("SELECT sum(size_bytes) FROM upload_chunks WHERE upload_session_id=?",Long.class,uploadId)).orElse(0L);
        int count=(int)((expected+CHUNK_BYTES-1)/CHUNK_BYTES);
        Integer chunkCount=jdbc.queryForObject("SELECT count(*) FROM upload_chunks WHERE upload_session_id=?",Integer.class,uploadId);
        if(actualSize!=expected || chunkCount==null || chunkCount!=count)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Upload is incomplete; upload every chunk before completing");
        Path assembled=root.resolve((String)s.get("object_key")).normalize();
        if(!assembled.startsWith(root)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid upload path");
        Files.createDirectories(assembled.getParent());
        Path temp=assembled.resolveSibling(assembled.getFileName()+".assembling");
        try(OutputStream out=Files.newOutputStream(temp,StandardOpenOption.CREATE_NEW)) {
            for(int i=0;i<count;i++) {
                String key=jdbc.queryForObject("SELECT object_key FROM upload_chunks WHERE upload_session_id=? AND chunk_index=?",String.class,uploadId,i);
                Path chunk=root.resolve(key).normalize();
                if(!chunk.startsWith(root)||!Files.isRegularFile(chunk)) throw new ResponseStatusException(HttpStatus.CONFLICT,"A stored chunk is missing; retry upload");
                try(InputStream in=Files.newInputStream(chunk)){in.transferTo(out);}
            }
        } catch(IOException | RuntimeException e){Files.deleteIfExists(temp);throw e;}
        String digest=sha256(temp);
        String expectedSha=(String)s.get("expected_sha256");
        if(expectedSha!=null&&!expectedSha.isBlank()&&!digest.equalsIgnoreCase(expectedSha.trim())){
            Files.deleteIfExists(temp);throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Full-file checksum mismatch; upload remains resumable");
        }
        try{Files.move(temp,assembled,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,assembled);}
        jdbc.update("INSERT INTO video_assets(video_id,asset_type,object_key,mime_type,size_bytes) VALUES (?,'ORIGINAL',?,?,?)",
            videoId,s.get("object_key"),s.get("content_type"),expected);
        jdbc.update("UPDATE videos SET status='PROCESSING',updated_at=now() WHERE id=? AND workspace_id=? AND deleted_at IS NULL",videoId,workspaceId);
        jdbc.update("UPDATE upload_sessions SET status='UPLOADED',completed_sha256=?,updated_at=now() WHERE id=?",digest,uploadId);
        jdbc.update("DELETE FROM upload_chunks WHERE upload_session_id=?",uploadId);
        deleteChunks(sessionDir(workspaceId,uploadId));
        processor.process(videoId,assembled,assembled.getParent());
        return Map.of("uploadId",uploadId.toString(),"videoId",videoId.toString(),"status","PROCESSING","sizeBytes",expected,"sha256",digest);
    }

    @GetMapping("/{uploadId}")
    public Map<String,Object> status(@PathVariable UUID uploadId,Authentication auth){
        Map<String,Object> s=session(uploadId,workspaces.id(auth),false);
        List<Map<String,Object>> chunks=jdbc.queryForList("SELECT chunk_index,size_bytes,sha256 FROM upload_chunks WHERE upload_session_id=? ORDER BY chunk_index",uploadId);
        return Map.of("uploadId",uploadId.toString(),"videoId",s.get("video_id").toString(),"status",s.get("status"),
            "expectedSizeBytes",s.get("expected_size_bytes"),"receivedBytes",chunks.stream().mapToLong(c->((Number)c.get("size_bytes")).longValue()).sum(),
            "chunks",chunks,"expiresAt",s.get("expires_at").toString());
    }

    @PostMapping("/{uploadId}/retry-processing")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,Object> retryProcessing(@PathVariable UUID uploadId,Authentication auth) {
        UUID workspaceId = workspaces.id(auth);
        workspaces.requireEditor(auth, workspaceId);
        Map<String,Object> s=session(uploadId,workspaceId,false);
        UUID videoId=(UUID)s.get("video_id");
        List<Map<String,Object>> videos=jdbc.queryForList("SELECT source_object_key,status FROM videos WHERE id=? AND workspace_id=? AND deleted_at IS NULL",videoId,workspaces.id(auth));
        if(videos.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Video not found");
        if(!"FAILED".equals(videos.get(0).get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only failed videos can be retried");
        Path source=root.resolve((String)videos.get(0).get("source_object_key")).normalize();
        if(!source.startsWith(root)||!Files.isRegularFile(source))throw new ResponseStatusException(HttpStatus.CONFLICT,"Original file is missing; upload again");
        jdbc.update("UPDATE videos SET status='PROCESSING',updated_at=now() WHERE id=?",videoId);
        processor.process(videoId,source,source.getParent());
        return Map.of("videoId",videoId.toString(),"status","PROCESSING");
    }

    private Map<String,Object> session(UUID id,UUID workspaceId,boolean requireActive){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT us.*,v.workspace_id FROM upload_sessions us JOIN videos v ON v.id=us.video_id WHERE us.id=? AND v.workspace_id=? AND v.deleted_at IS NULL",id,workspaceId);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Upload session not found");
        Map<String,Object> s=rows.get(0);
        if(requireActive){
            if(!Set.of("CREATED","UPLOADING").contains(s.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Upload session is not active");
            if(((java.sql.Timestamp)s.get("expires_at")).toInstant().isBefore(Instant.now())) {
                jdbc.update("UPDATE upload_sessions SET status='EXPIRED',updated_at=now() WHERE id=?",id);
                throw new ResponseStatusException(HttpStatus.GONE,"Upload session expired");
            }
        }
        return s;
    }
    private Path sessionDir(UUID workspaceId,UUID id){return root.resolve("workspaces").resolve(workspaceId.toString()).resolve("uploads").resolve(id.toString()).normalize();}
    private String extension(String filename){
        String name=filename==null?"video.mp4":filename;
        String ext=name.lastIndexOf('.')>=0?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"mp4";
        if(!Set.of("mp4","mov","webm","mkv").contains(ext))throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Unsupported file extension");
        return ext;
    }
    private static String normalizeSha(String value){
        if(value==null||value.isBlank())return null;
        if(!SHA.matcher(value).matches())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"sha256 must be a SHA-256 hex digest");
        return value.toLowerCase(Locale.ROOT);
    }
    private static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String sha256(Path file)throws IOException{try{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(file)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void deleteChunks(Path dir){try(var paths=Files.list(dir)){paths.filter(p->p.getFileName().toString().endsWith(".part")).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}}
}
