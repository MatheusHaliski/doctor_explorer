/**
 * RF06 · cofre de chaves do aparelho no navegador: IndexedDB com cada valor cifrado (AES-256-GCM) por uma chave
 * gerada aqui como **não exportável** (o script da página usa, mas não consegue ler os bytes dela). Um banco por
 * conta, para dois logins no mesmo navegador não se misturarem. Nada disso vai para o servidor.
 */
import type { KeyStore } from "@doctor-explorer/crypto";

const STORE = "kv";
const WRAP = "__wrap";

function request<T>(r: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(r.error);
  });
}

function open(name: string): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const r = indexedDB.open(name, 1);
    r.onupgradeneeded = () => r.result.createObjectStore(STORE);
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(r.error);
  });
}

export class IndexedDbStore implements KeyStore {
  private constructor(
    private readonly db: IDBDatabase,
    private readonly key: CryptoKey,
  ) {}

  static async open(userId: string): Promise<IndexedDbStore> {
    const db = await open(`doctor-explorer-chat-${userId}`);
    const tx = db.transaction(STORE, "readonly");
    let key = (await request(tx.objectStore(STORE).get(WRAP))) as CryptoKey | undefined;
    if (!key) {
      key = await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
      await request(db.transaction(STORE, "readwrite").objectStore(STORE).put(key, WRAP));
    }
    return new IndexedDbStore(db, key);
  }

  async get(k: string): Promise<Uint8Array | undefined> {
    const v = (await request(this.db.transaction(STORE, "readonly").objectStore(STORE).get("v:" + k))) as { iv: Uint8Array; data: ArrayBuffer } | undefined;
    if (!v) return undefined;
    return new Uint8Array(await crypto.subtle.decrypt({ name: "AES-GCM", iv: v.iv.slice().buffer as ArrayBuffer, additionalData: new TextEncoder().encode(k) }, this.key, v.data));
  }

  async put(k: string, value: Uint8Array): Promise<void> {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const data = await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: new TextEncoder().encode(k) }, this.key, value.slice().buffer as ArrayBuffer);
    await request(this.db.transaction(STORE, "readwrite").objectStore(STORE).put({ iv, data }, "v:" + k));
  }

  async delete(k: string): Promise<void> {
    await request(this.db.transaction(STORE, "readwrite").objectStore(STORE).delete("v:" + k));
  }

  async keys(prefix: string): Promise<string[]> {
    const all = (await request(this.db.transaction(STORE, "readonly").objectStore(STORE).getAllKeys())) as IDBValidKey[];
    return all.map(String).filter((k) => k.startsWith("v:" + prefix)).map((k) => k.slice(2));
  }
}
