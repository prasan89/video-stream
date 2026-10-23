"use client";
import Link from "next/link";
import {useParams} from "next/navigation";
import {useEffect,useState} from "react";
import VideoPlayer from "../../dashboard/VideoPlayer";
const API=process.env.NEXT_PUBLIC_API_BASE_URL||"http://localhost:8080/api/v1";
type SharedVideo={id:string;title:string;description:string|null;status:string;playbackPath:string};
export default function SharePage(){
 const params=useParams<{token:string}>();const token=params.token;const[data,setData]=useState<SharedVideo|null>(null);const[error,setError]=useState("");const[loading,setLoading]=useState(true);
 useEffect(()=>{let cancelled=false;fetch(`${API}/shared/${encodeURIComponent(token)}`).then(async r=>{const d=await r.json();if(!r.ok)throw new Error(d.message||"This share link is invalid or revoked.");return d as SharedVideo;}).then(d=>{if(!cancelled)setData(d);}).catch(e=>{if(!cancelled)setError(e instanceof Error?e.message:"Unable to load shared video.");}).finally(()=>{if(!cancelled)setLoading(false);});return()=>{cancelled=true;};},[token]);
 return <main className="shell subPage"><nav className="nav"><Link className="brand" href="/">VideoCloud<span>●</span></Link><Link href="/dashboard">Creator studio</Link></nav>{loading?<p role="status">Loading shared video…</p>:error?<section className="signupCard"><h1>Video unavailable</h1><p role="alert">{error}</p></section>:data?<section className="features"><p className="eyebrow">SHARED VIDEO</p><h1>{data.title}</h1>{data.description&&<p className="heroCopy">{data.description}</p>}{data.status==="READY"?<VideoPlayer videoId={data.id} auth="" sourcePath={data.playbackPath}/>:<p>This video is still processing. Ask the owner to try again later.</p>}</section>:null}</main>;
}
