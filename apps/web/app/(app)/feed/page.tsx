import type { Metadata } from "next";
import { FeedView } from "./FeedView";

export const metadata: Metadata = { title: "Início" };

export default function FeedPage() {
  return <FeedView />;
}
