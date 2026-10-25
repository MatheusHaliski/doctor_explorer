"use client";

import { DeviceGate } from "@/components/chat/DeviceGate";
import { Inbox } from "@/components/chat/Inbox";

export default function MessagesPage() {
  return <DeviceGate>{(device) => <Inbox device={device} />}</DeviceGate>;
}
