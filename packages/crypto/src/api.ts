/**
 * O que o cliente MLS precisa do Delivery Service (core-api, /api/v1/messaging). O web implementa com fetch; os
 * testes, com um servidor em memória que segue as mesmas regras (época, KeyPackage de uso único, Welcome por aparelho).
 */

export type DeviceState = "PENDING" | "APPROVED" | "REVOKED";

export interface DeviceInfo {
  id: string;
  userId: string;
  userName?: string;
  name: string;
  signatureKey: string;
  state: DeviceState;
  freeKeyPackages: number;
}

export interface MemberInfo {
  userId: string;
  name: string;
  role: "OWNER" | "MEMBER";
  verified: boolean;
}

export interface ConversationInfo {
  id: string;
  kind: "DIRECT" | "GROUP";
  title: string | null;
  epoch: number;
  retentionDays: number;
  members: MemberInfo[];
  devices: string[];
  devicesToAdd: { deviceId: string; userId: string; hasKeyPackage: boolean }[];
  devicesToRemove: string[];
}

export interface Envelope {
  seq: number;
  kind: "APP" | "COMMIT";
  epoch: number;
  senderDeviceId: string;
  senderUserId: string;
  payload: string;
  createdAt: string;
}

export interface WelcomeItem {
  id: string;
  conversationId: string;
  epoch: number;
  payload: string;
}

export interface NewConversation {
  kind: "DIRECT" | "GROUP";
  memberIds: string[];
  title?: string;
  retentionDays?: number;
  deviceId: string;
}

export interface CommitBody {
  epoch: number;
  senderDeviceId: string;
  commit: string;
  welcome?: string;
  addedDeviceIds?: string[];
  removedDeviceIds?: string[];
}

export interface DeliveryApi {
  registerDevice(name: string, signatureKey: string, recoveryVerifier?: string): Promise<DeviceInfo>;
  device(id: string): Promise<DeviceInfo>;
  myDevices(): Promise<DeviceInfo[]>;
  publishKeyPackages(deviceId: string, keyPackages: string[]): Promise<number>;
  welcomes(deviceId: string): Promise<WelcomeItem[]>;
  ackWelcome(deviceId: string, welcomeId: string): Promise<void>;
  createConversation(input: NewConversation): Promise<ConversationInfo>;
  conversation(id: string): Promise<ConversationInfo>;
  claimKeyPackages(conversationId: string, deviceIds: string[]): Promise<{ deviceId: string; userId: string; keyPackage: string }[]>;
  commit(conversationId: string, body: CommitBody): Promise<{ seq: number; epoch: number }>;
  send(conversationId: string, body: { epoch: number; senderDeviceId: string; payload: string }): Promise<{ seq: number; epoch: number }>;
  messages(conversationId: string, after: number): Promise<Envelope[]>;
}

/** 409 "epoca-desatualizada": outro aparelho mudou o grupo antes; sincronize e refaça. */
export class StaleEpochError extends Error {
  constructor() {
    super("época desatualizada");
  }
}

/**
 * 409 "aparelho-fora-do-grupo": este aparelho ainda não está na árvore desta conversa (aguarda Welcome).
 */
export class NotInGroupError extends Error {
  constructor() {
    super("aparelho fora do grupo");
  }
}
