"use client";

import { DeviceGate } from "@/components/chat/DeviceGate";
import { Devices } from "@/components/chat/Devices";

export default function DevicesPage() {
  return <DeviceGate>{(device, refresh) => <Devices device={device} onChange={refresh} />}</DeviceGate>;
}
