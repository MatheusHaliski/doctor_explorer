import type { Metadata } from "next";
import { Suspense } from "react";
import { Loading } from "@/components/ui";
import { DirectoryView } from "./DirectoryView";

export const metadata: Metadata = { title: "Instituições" };

export default function InstitutionsPage() {
  return (
    <Suspense fallback={<Loading />}>
      <DirectoryView />
    </Suspense>
  );
}
