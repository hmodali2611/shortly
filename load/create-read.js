import http from "k6/http";
import { check, fail, sleep } from "k6";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const creationCount = 100;

export const options = {
  setupTimeout: "3m",
  scenarios: {
    reads: {
      executor: "shared-iterations",
      vus: 100,
      iterations: 10000,
      maxDuration: "2m",
    },
  },
  thresholds: {
    checks: ["rate==1"],
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<100"],
  },
};

export function setup() {
  const shortCodes = [];
  const millisecondsUntilNextMinute = 60000 - (Date.now() % 60000);
  sleep((millisecondsUntilNextMinute + 100) / 1000);

  for (let index = 0; index < creationCount; index += 1) {
    const response = http.post(
      `${baseUrl}/api/v1/links`,
      JSON.stringify({ targetUrl: `https://example.com/load-test/${index}` }),
      { headers: { "Content-Type": "application/json" } },
    );
    const shortCode = response.json("shortCode");
    const created = check(response, {
      "URL created": (result) => result.status === 201,
      "short code returned": () => typeof shortCode === "string" && shortCode.length > 0,
    });

    if (!created) {
      fail(`Creation ${index + 1} failed with status ${response.status}`);
    }
    shortCodes.push(shortCode);
    sleep(1.05);
  }

  if (new Set(shortCodes).size !== creationCount) {
    fail("The 100 generated short codes were not unique");
  }

  return { shortCodes };
}

export default function (data) {
  const shortCode = data.shortCodes[__ITER % data.shortCodes.length];
  const response = http.get(`${baseUrl}/${shortCode}`, { redirects: 0 });

  check(response, {
    "redirect returned": (result) => result.status === 302,
    "location preserved": (result) => result.headers.Location.startsWith(
      "https://example.com/load-test/",
    ),
  });
}