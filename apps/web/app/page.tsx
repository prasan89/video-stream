import Link from "next/link";
const features = [
  ["Professional player", "Responsive, ad-free playback designed for your website."],
  ["Privacy controls", "Build toward private sharing and domain-restricted embeds."],
  ["Simple usage plans", "INR billing and clear storage and bandwidth allowances."],
];

export default function HomePage() {
  return (
    <main className="shell">
      <nav className="nav">
        <a className="brand" href="/">VideoCloud<span>●</span></Link>
        <div className="navLinks"><Link href="#features">Features</Link><Link href="/pricing">Pricing</Link><a className="button buttonSmall" href="/signup">Get started</Link></div>
      </nav>
      <section className="hero">
        <p className="eyebrow">VIDEO HOSTING, WITHOUT THE COMPLEXITY</p>
        <h1>Your videos.<br /><em>Your brand.</em></h1>
        <p className="heroCopy">A professional home for your business videos, lessons and product demos. Upload, manage and share from one workspace.</p>
        <div className="heroActions"><a className="button" href="/signup">Start building <span>→</span></Link><a className="textLink" href="/pricing">Explore plans</Link></div>
        <div className="mockPlayer"><div className="mockTop"><span>VIDEO PREVIEW</span><span>1080p · PRIVATE</span></div><div className="play">▶</div><div className="mockBottom"><span>Introducing your next big idea</span><span>▶ ━━━━━━━━ 04:32</span></div></div>
      </section>
      <section id="features" className="features"><p className="eyebrow">MADE FOR YOUR WORKFLOW</p><h2>Everything your videos need.</h2><div className="featureGrid">{features.map(([title, description]) => <article className="feature" key={title}><div className="featureMark">↗</div><h3>{title}</h3><p>{description}</p></article>)}</div></section>
      <footer><a className="brand" href="/">VideoCloud<span>●</span></Link><span>Independent product · Built for creators and businesses</span></footer>
    </main>
  );
}
