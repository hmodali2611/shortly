import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const shortCode = __ENV.SHORT_CODE;

if (!shortCode) {
  throw new Error("SHORT_CODE is required");
}

export const options = {
  scenarios: {
    redirects: {
      executor: "constant-vus",
      vus: 10,
      duration: "30s",
    },
  },
  thresholds: {
    checks: ["rate==1"],
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500"],
  },
};

export default function () {
  const response = http.get(`${baseUrl}/${shortCode}`, { redirects: 0 });
  check(response, {
    "redirect returned": (result) => result.status === 302,
    "location preserved": (result) => result.headers.Location === "https://example.com/cache-benchmark",
  });
  sleep(0.1);
}