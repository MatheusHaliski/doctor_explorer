"use client";

/** RF06 · o aparelho de chat deste navegador: carrega do IndexedDB, registra, recupera pela frase e repõe KeyPackages. */
import { ChatDevice, recoveryVerifier, type DeviceInfo } from "@doctor-explorer/crypto";
import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { deliveryApi } from "@/lib/chat-api";
import { IndexedDbStore } from "@/lib/chat-store";

export type DeviceStatus =
  | { kind: "loading" }
  | { kind: "unsupported" }
  | { kind: "none"; firstDevice: boolean }
  | { kind: "pending"; device: ChatDevice }
  | { kind: "revoked"; device: ChatDevice }
  | { kind: "ready"; device: ChatDevice; others: DeviceInfo[] }
  | { kind: "error"; error: unknown };

const stores = new Map<string, Promise<IndexedDbStore>>();
function storeFor(userId: string) {
  let s = stores.get(userId);
  if (!s) {
    s = IndexedDbStore.open(userId);
    stores.set(userId, s);
    s.catch(() => stores.delete(userId));
  }
  return s;
}

const devices = new Map<string, ChatDevice>();

async function resolve(userId: string): Promise<DeviceStatus> {
  if (typeof indexedDB === "undefined" || !crypto?.subtle) return { kind: "unsupported" };
  const store = await storeFor(userId);
  const device = devices.get(userId) ?? (await ChatDevice.load(store, deliveryApi, userId));
  const mine = await deliveryApi.myDevices();
  if (!device) return { kind: "none", firstDevice: !mine.some((d) => d.state === "APPROVED") };
  devices.set(userId, device);
  const info = mine.find((d) => d.id === device.deviceId);
  if (!info || info.state === "REVOKED") return { kind: "revoked", device };
  if (info.state === "PENDING") return { kind: "pending", device };
  await device.ensureKeyPackages(info.freeKeyPackages);
  return { kind: "ready", device, others: mine.filter((d) => d.id !== device.deviceId) };
}

export function useChatDevice(userId: string) {
  const [status, setStatus] = useState<DeviceStatus>({ kind: "loading" });

  const refresh = useCallback(async () => {
    try {
      setStatus(await resolve(userId));
    } catch (error) {
      setStatus({ kind: "error", error });
    }
  }, [userId]);

  useEffect(() => {
    let alive = true;
    resolve(userId)
      .then((s) => alive && setStatus(s))
      .catch((error) => alive && setStatus({ kind: "error", error }));
    return () => {
      alive = false;
    };
  }, [userId]);

  /** Registra este navegador. No primeiro aparelho da conta devolve a frase de recuperação, para mostrar uma vez. */
  const register = useCallback(
    async (name: string, firstDevice: boolean): Promise<string | null> => {
      const store = await storeFor(userId);
      const r = await ChatDevice.register(store, deliveryApi, userId, name, firstDevice);
      devices.set(userId, r.device);
      await refresh();
      return r.recoveryPhrase;
    },
    [userId, refresh],
  );

  const recover = useCallback(
    async (device: ChatDevice, phrase: string) => {
      const verifier = await recoveryVerifier(phrase, userId);
      await api.post(`/api/v1/messaging/devices/${device.deviceId}/recover`, { verifier });
      await refresh();
    },
    [userId, refresh],
  );

  /** Depois de revogado: apaga as chaves deste navegador para poder registrá-lo de novo. */
  const forget = useCallback(
    async (device: ChatDevice) => {
      await device.forget();
      devices.delete(userId);
      await refresh();
    },
    [userId, refresh],
  );

  return { status, refresh, register, recover, forget };
}
