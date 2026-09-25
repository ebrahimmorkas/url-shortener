// k6 load test: creates a pool of links, then hammers the redirect endpoint.
// Run: docker run --rm -i --network host grafana/k6 run - < load-test/redirects.js
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
// Stays under the create-link rate limit (20/min)
const LINKS = 15;

export const options = {
  scenarios: {
    redirects: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 50),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    // Regression guard sized for a laptop running the whole stack; see README for measured numbers
    'http_req_duration{name:redirect}': ['p(95)<250'],
  },
};

export function setup() {
  const codes = [];
  for (let i = 0; i < LINKS; i++) {
    const res = http.post(`${BASE_URL}/api/links`, JSON.stringify({ url: `https://example.com/page/${i}` }), {
      headers: { 'Content-Type': 'application/json' },
    });
    check(res, { 'link created': (r) => r.status === 201 });
    codes.push(res.json('code'));
  }
  return { codes };
}

export default function (data) {
  const code = data.codes[Math.floor(Math.random() * data.codes.length)];
  const res = http.get(`${BASE_URL}/${code}`, { redirects: 0, tags: { name: 'redirect' } });
  check(res, { 'is 302': (r) => r.status === 302 });
}
