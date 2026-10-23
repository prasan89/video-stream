"use client";
import {useEffect,useRef,useState} from "react";
import Hls from "hls.js";
const API=process.env.NEXT_PUBLIC_API_BASE_URL||"http://localhost:8080/api/v1";
export default function VideoPlayer({videoId,auth,sourcePath}:{videoId:string;auth:string;sourcePath?:string}){
 const ref=useRef<HTMLVideoElement>(null);const [error,setError]=useState("");
 useEffect(()=>{const video=ref.current;if(!video)return;const relativePath=sourcePath?.startsWith("/api/v1/")?sourcePath.slice("/api/v1".length):sourcePath;const url=relativePath?`${API}${relativePath}`:`${API}/videos/${videoId}/assets/index.m3u8`;let hls:Hls|undefined;
 if(Hls.isSupported()){hls=new Hls({xhrSetup:(xhr)=>{if(auth)xhr.setRequestHeader("Authorization",`Basic ${auth}`);}});hls.loadSource(url);hls.attachMedia(video);hls.on(Hls.Events.ERROR,(_,data)=>{if(data.fatal)setError("Playback failed. Refresh processing status and check FFmpeg output.");});}
 else if(video.canPlayType("application/vnd.apple.mpegurl")){setError("This browser's native HLS player cannot attach the local Basic-auth header. Use Chrome/Firefox with hls.js for this local MVP.");}
 else setError("HLS playback is not supported in this browser.");
 return()=>{hls?.destroy();};},[videoId,auth]);
 return <div><video ref={ref} controls preload="metadata" width="100%" aria-label="Video playback"/>{error&&<p role="status">{error}</p>}</div>;
}
