"use client";
import {useEffect,useRef,useState} from "react";
import Hls from "hls.js";
const API=process.env.NEXT_PUBLIC_API_BASE_URL||"http://localhost:8080/api/v1";
type PlaybackEvent = "PLAY" | "PAUSE" | "COMPLETE" | "ERROR";
export default function VideoPlayer({videoId,auth,workspaceId,sourcePath}:{videoId:string;auth:string;workspaceId:string;sourcePath?:string}){
 const ref=useRef<HTMLVideoElement>(null);const lastReportedTime=useRef(0);const [error,setError]=useState("");
 useEffect(()=>{
  const video=ref.current;if(!video)return;
  const relativePath=sourcePath?.startsWith("/api/v1/")?sourcePath.slice("/api/v1".length):sourcePath;
  const url=relativePath?`${API}${relativePath}`:`${API}/videos/${videoId}/assets/index.m3u8`;
  let hls:Hls|undefined;
  if(Hls.isSupported()){
   hls=new Hls({xhrSetup:(xhr)=>{if(auth)xhr.setRequestHeader("Authorization",`Basic ${auth}`);if(workspaceId)xhr.setRequestHeader("X-Workspace-Id",workspaceId);}});
   hls.loadSource(url);hls.attachMedia(video);
   hls.on(Hls.Events.ERROR,(_,data)=>{if(data.fatal){setError("Playback failed. Refresh processing status and check FFmpeg output.");sendEvent("ERROR");}});
  } else if(video.canPlayType("application/vnd.apple.mpegurl")){
   setError("This browser's native HLS player cannot attach the local Basic-auth header. Use Chrome/Firefox with hls.js for this local MVP.");
  } else setError("HLS playback is not supported in this browser.");

  function sendEvent(eventType:PlaybackEvent,watchedSeconds=0){
   if(!auth)return; // Anonymous shared-link analytics are deliberately not collected yet.
   let viewerKey="";
   try{
    viewerKey=sessionStorage.getItem("vc-viewer-key")||crypto.randomUUID();
    sessionStorage.setItem("vc-viewer-key",viewerKey);
   }catch{return;}
   void fetch(`${API}/analytics/videos/${videoId}/events`,{
    method:"POST",
    headers:{"Content-Type":"application/json",Authorization:`Basic ${auth}`,"X-Workspace-Id":workspaceId},
    body:JSON.stringify({eventType,watchedSeconds:Math.max(0,Math.min(86400,Math.floor(watchedSeconds))),viewerKey}),
    keepalive:true
   }).catch(()=>{});
  }
  const onPlay=()=>sendEvent("PLAY");
  const onPause=()=>{if(!video.ended)sendEvent("PAUSE");};
  const onEnded=()=>sendEvent("COMPLETE");
  video.addEventListener("play",onPlay);video.addEventListener("pause",onPause);video.addEventListener("ended",onEnded);
  return()=>{video.removeEventListener("play",onPlay);video.removeEventListener("pause",onPause);video.removeEventListener("ended",onEnded);hls?.destroy();};
 },[videoId,auth,workspaceId,sourcePath]);
 return <div><video ref={ref} controls preload="metadata" width="100%" aria-label="Video playback"/>{error&&<p role="status">{error}</p>}</div>;
}
