package com.videocloud.video;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
@Service public class VideoProcessingService {
 private final JdbcTemplate jdbc;private final String ffmpeg;
 public VideoProcessingService(JdbcTemplate jdbc,@Value("${videocloud.ffmpeg-path:ffmpeg}")String ffmpeg){this.jdbc=jdbc;this.ffmpeg=ffmpeg;}
 @Async public void process(UUID id,Path source,Path dir){try{Files.createDirectories(dir);Process thumb=new ProcessBuilder(ffmpeg,"-hide_banner","-loglevel","error","-y","-ss","00:00:01","-i",source.toString(),"-frames:v","1",dir.resolve("thumbnail.jpg").toString()).redirectErrorStream(true).start();if(!thumb.waitFor(3,TimeUnit.MINUTES)||thumb.exitValue()!=0)throw new IllegalStateException("thumbnail failed");Process hls=new ProcessBuilder(ffmpeg,"-hide_banner","-loglevel","error","-y","-i",source.toString(),"-vf","scale=w='min(1280,iw)':h=-2","-c:v","libx264","-preset","veryfast","-crf","25","-c:a","aac","-b:a","128k","-hls_time","6","-hls_playlist_type","vod","-hls_segment_filename",dir.resolve("segment-%05d.ts").toString(),dir.resolve("index.m3u8").toString()).redirectErrorStream(true).start();if(!hls.waitFor(2,TimeUnit.HOURS)||hls.exitValue()!=0)throw new IllegalStateException("HLS transcode failed");jdbc.update("UPDATE videos SET status='READY',updated_at=now() WHERE id=?",id);}catch(Exception e){jdbc.update("UPDATE videos SET status='FAILED',updated_at=now() WHERE id=?",id);}}
}