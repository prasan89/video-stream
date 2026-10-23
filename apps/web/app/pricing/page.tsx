const plans = [
  { name: "Free", price: "₹0", desc: "Try the workflow", features: ["2 GB storage", "5 videos", "Basic player"] },
  { name: "Starter", price: "₹499", desc: "For individuals", features: ["50 GB storage", "25 videos", "Privacy controls"] },
  { name: "Pro", price: "₹1,499", desc: "For growing teams", features: ["250 GB storage", "200 videos", "Analytics and team tools"] },
];

export default function PricingPage() {
  return <main className="shell subPage"><a className="brand" href="/">VideoCloud<span>●</span></a><p className="eyebrow">SIMPLE, TRANSPARENT PLANS</p><h1>Choose your workspace.</h1><p className="heroCopy">Illustrative launch pricing in INR. Final quotas and billing terms must be validated against delivery costs before checkout is enabled.</p><div className="priceGrid">{plans.map((plan) => <article className="priceCard" key={plan.name}><h2>{plan.name}</h2><p>{plan.desc}</p><div className="price">{plan.price}<small>{plan.price === "₹0" ? "" : " / month"}</small></div><ul>{plan.features.map((feature) => <li key={feature}>✓ {feature}</li>)}</ul><a className="button" href="/signup">Get started</a></article>)}</div><p className="disclaimer">Plans are placeholders for product development. No payment is collected by this demo.</p></main>;
}
