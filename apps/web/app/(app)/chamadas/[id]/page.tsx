"use client";

import { use } from "react";
import { CallView } from "@/components/call/CallView";
import { DeviceGate } from "@/components/chat/DeviceGate";

export default function CallPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return <DeviceGate>{(device) => <CallView id={id} device={device} />}</DeviceGate>;
}
