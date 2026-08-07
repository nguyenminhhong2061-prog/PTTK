const baseUrl = (process.env.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const mode = process.env.MODE;
const submitUrl = `${baseUrl}/api/submissions/tv4-gateway-probe/submit`;

if (!['rate-limit', 'bad-gateway', 'timeout'].includes(mode)) {
  throw new Error('MODE must be rate-limit, bad-gateway, or timeout.');
}

async function requestSubmit() {
  const response = await fetch(submitUrl, {method: 'POST'});
  const bodyText = await response.text();
  let body;
  try {
    body = JSON.parse(bodyText);
  } catch {
    throw new Error(`Expected JSON but received ${response.status}: ${bodyText}`);
  }
  return {response, body};
}

function assertGatewayError(result, status, errorCode) {
  if (result.response.status !== status) {
    throw new Error(`Expected HTTP ${status}, received ${result.response.status}: ${JSON.stringify(result.body)}`);
  }
  if (result.body?.error !== errorCode) {
    throw new Error(`Expected error ${errorCode}, received: ${JSON.stringify(result.body)}`);
  }
  if (result.response.headers.get('content-type')?.includes('application/json') !== true) {
    throw new Error(`Expected application/json, received ${result.response.headers.get('content-type')}`);
  }
}

if (mode === 'rate-limit') {
  const results = await Promise.all(Array.from({length: 50}, requestSubmit));
  const rateLimited = results.filter(({response}) => response.status === 429);
  if (rateLimited.length === 0) {
    throw new Error(`Expected at least one 429 response; received ${results.map(({response}) => response.status).join(', ')}`);
  }
  rateLimited.forEach((result) => {
    assertGatewayError(result, 429, 'RATE_LIMIT_EXCEEDED');
    if (result.response.headers.get('retry-after') !== '1') {
      throw new Error(`Expected Retry-After: 1, received ${result.response.headers.get('retry-after')}`);
    }
  });
  process.stdout.write(`Rate-limit probe passed: ${rateLimited.length}/50 requests returned 429.\n`);
} else if (mode === 'bad-gateway') {
  assertGatewayError(await requestSubmit(), 502, 'BAD_GATEWAY');
  process.stdout.write('502 JSON probe passed.\n');
} else {
  assertGatewayError(await requestSubmit(), 504, 'GATEWAY_TIMEOUT');
  process.stdout.write('504 JSON probe passed.\n');
}
