import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const apiKey = __ENV.API_KEY || "dev-key";
const headers = {
  Authorization: `Bearer ${apiKey}`,
  "Content-Type": "application/json",
};

export const options = {
  scenarios: {
    redirects: {
      executor: "constant-vus",
      vus: 10,
      duration: "30s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<100"],
  },
};

export function setup() {
  const response = http.post(
    `${baseUrl}/api/v1/links`,
    JSON.stringify({ targetUrl: "https://example.com/load-test" }),
    { headers },
  );
  check(response, { "link created": (result) => result.status === 201 });
  return { shortCode: response.json("shortCode") };
}

export default function (data) {
  const response = http.get(`${baseUrl}/${data.shortCode}`, { redirects: 0 });
  check(response, {
    "redirect returned": (result) => result.status === 302,
    "location preserved": (result) => result.headers.Location === "https://example.com/load-test",
  });
  sleep(0.1);
}

export function teardown(data) {
  const metadata = http.get(`${baseUrl}/api/v1/links/${data.shortCode}`, { headers });
  const stats = http.get(`${baseUrl}/api/v1/links/${data.shortCode}/stats`, { headers });
  check(metadata, { "metadata available": (result) => result.status === 200 });
  check(stats, { "stats available": (result) => result.status === 200 });
}