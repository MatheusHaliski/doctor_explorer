"use client";

import { use } from "react";
import { Conversation } from "@/components/chat/Conversation";
import { DeviceGate } from "@/components/chat/DeviceGate";

export default function ConversationPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return <DeviceGate>{(device) => <Conversation id={id} device={device} />}</DeviceGate>;
}
