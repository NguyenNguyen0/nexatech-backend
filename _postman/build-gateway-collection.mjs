import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function prefixGatewayUrl(url) {
  if (!url || !Array.isArray(url.path)) return;
  url.host = ["{{gatewayBaseUrl}}"];
  url.path = ["api", "v1", ...url.path];
  const q =
    url.query?.length > 0
      ? "?" +
        url.query
          .filter((x) => !x.disabled)
          .map((x) => {
            const v = x.value ?? "";
            if (v.includes("{{")) return `${x.key}=${v}`;
            return `${encodeURIComponent(x.key)}=${encodeURIComponent(v)}`;
          })
          .join("&")
      : "";
  url.raw = `{{gatewayBaseUrl}}/${url.path.join("/")}${q}`;
}

const identity = JSON.parse(
  fs.readFileSync(path.join(__dirname, "Identity Service API Check (Nexatech).postman_collection.json"), "utf8")
);
const user = JSON.parse(
  fs.readFileSync(path.join(__dirname, "User Service API Check (Nexatech).postman_collection.json"), "utf8")
);

const authFolder = structuredClone(identity.item.find((x) => x.name?.includes("1. Auth")));
if (!authFolder) throw new Error("Auth folder not found in Identity collection");
authFolder.name = "1. Auth (qua API Gateway)";

function walkItems(items) {
  for (const it of items) {
    if (it.item) walkItems(it.item);
    if (it.request?.url) prefixGatewayUrl(it.request.url);
  }
}
walkItems(authFolder.item);

const userItems = user.item.filter((x) => x.name !== "Login for test API User Service");
for (const folder of userItems) {
  walkItems(folder.item || []);
}

const loginItem = structuredClone(user.item.find((x) => x.name === "Login for test API User Service"));
if (loginItem?.request?.url) prefixGatewayUrl(loginItem.request.url);

const varMap = new Map();
for (const v of identity.variable || []) {
  if (v.key === "identityBaseUrl") continue;
  varMap.set(v.key, { key: v.key, value: v.value });
}
for (const v of user.variable || []) {
  if (v.key === "identityBaseUrl" || v.key === "userBaseUrl") continue;
  varMap.set(v.key, { key: v.key, value: v.value });
}
const variables = [
  { key: "gatewayBaseUrl", value: "http://localhost:8080" },
  ...Array.from(varMap.values()),
];

const out = {
  info: {
    name: "API Gateway — Identity Auth & User Service (Nexatech)",
    description:
      "Gọi qua **API Gateway** (`api/v1/...`, port mặc định 8080).\n\nToken: **Login** → `accessToken` / `refreshToken` (script test giống collection cũ). Các request cần JWT: header **Authorization: Bearer {{accessToken}}**.",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    _postman_id: "a1b2c3d4-gateway-nexatech-identity-user",
  },
  item: [
    { name: authFolder.name, item: authFolder.item },
    ...userItems,
    loginItem,
  ],
  variable: variables,
};

fs.writeFileSync(
  path.join(__dirname, "API Gateway - Identity Auth & User Service (Nexatech).postman_collection.json"),
  JSON.stringify(out, null, 2),
  "utf8"
);
console.log("Written API Gateway collection.");
