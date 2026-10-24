import { redirect } from "next/navigation";
import { getSession } from "@/lib/server-api";

export default async function Home() {
  const s = await getSession();
  if (!s) redirect("/login");
  redirect(s.status === "PENDING_VERIFICATION" ? "/verificar-email" : "/feed");
}
