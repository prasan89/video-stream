import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "VideoCloud — Professional video hosting",
  description: "Host, manage and share professional videos.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
