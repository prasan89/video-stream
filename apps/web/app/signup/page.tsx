import Link from "next/link";
export default function SignupPage() {
  return <main className="shell subPage"><Link className="brand" href="/">VideoCloud<span>●</span></Link><section className="signupCard"><p className="eyebrow">YOUR WORKSPACE STARTS HERE</p><h1>Create your account</h1><p className="heroCopy">Authentication is the next Phase 1 implementation task. This scaffold does not yet create accounts.</p><form action="/pricing"><label htmlFor="email">Work email</label><input id="email" type="email" placeholder="you@company.com" required /><label htmlFor="workspace">Workspace name</label><input id="workspace" placeholder="Acme Studio" required /><button className="button" type="submit">Continue to plans</button></form></section></main>;
}
