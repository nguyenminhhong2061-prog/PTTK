import http from 'k6/http';
import exec from 'k6/execution';
import {check} from 'k6';
import {Counter, Rate} from 'k6/metrics';
import {SharedArray} from 'k6/data';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const scenario = __ENV.SCENARIO || 'spike';
const runId = __ENV.RUN_ID || new Date().toISOString().replace(/[^0-9]/g, '');

const submissions = new SharedArray('submissions', () =>
  JSON.parse(open('./fixtures/submissions.json'))
);

const submitSuccess = new Counter('submit_success');
const submitConflict = new Counter('submit_conflict');
const rateLimited = new Counter('rate_limited');
const serviceUnavailable = new Counter('service_unavailable');
const gatewayTimeout = new Counter('gateway_timeout');
const unexpectedResponse = new Counter('unexpected_response');
const acceptedResponse = new Rate('accepted_response');

// Throttling is an expected gateway protection result, not a failed HTTP
// transport request. Business failures remain unexpected.
http.setResponseCallback(http.expectedStatuses({min: 200, max: 399}, 429));

const scenarioOptions = scenario === 'steady'
  ? {
      steady: {
        executor: 'constant-arrival-rate',
        rate: 10,
        timeUnit: '1s',
        duration: '20s',
        preAllocatedVUs: 20,
        maxVUs: 50,
      },
    }
  : {
      spike: {
        executor: 'per-vu-iterations',
        vus: Math.min(200, submissions.length),
        iterations: 1,
        maxDuration: '2m',
      },
    };

export const options = {
  scenarios: scenarioOptions,
  thresholds: {
    accepted_response: ['rate>0.95'],
    http_req_duration: ['p(95)<11000'],
  },
};

export default function () {
  const index = exec.scenario.iterationInTest % submissions.length;
  const item = submissions[index];
  const submitUrl = `${baseUrl}/api/submissions/${item.submissionId}/submit`;
  const response = http.post(submitUrl, null, {
    headers: {'Content-Type': 'application/json'},
    tags: {endpoint: 'submit'},
  });

  let accepted = true;
  switch (response.status) {
    case 200:
      submitSuccess.add(1);
      break;
    case 409:
      submitConflict.add(1);
      accepted = false;
      break;
    case 429:
      rateLimited.add(1);
      break;
    case 503:
      serviceUnavailable.add(1);
      accepted = false;
      break;
    case 504:
      gatewayTimeout.add(1);
      accepted = false;
      // A timeout is ambiguous. Inspect state instead of automatically
      // repeating a non-idempotent POST.
      http.get(`${baseUrl}/api/submissions/${item.submissionId}`, {
        tags: {endpoint: 'submission_state_after_timeout'},
      });
      break;
    default:
      unexpectedResponse.add(1);
      accepted = false;
  }

  acceptedResponse.add(accepted);
  check(response, {
    'submit returned an expected status': (res) => [200, 409, 429, 503, 504].includes(res.status),
  });
}

export function handleSummary(data) {
  return {
    stdout: `TV4 ${scenario} load test complete (run ${runId}).\n`,
    [`scripts/load-test/results/${runId}-${scenario}-summary.json`]: JSON.stringify(data, null, 2),
  };
}
