#!/usr/bin/env bash
# ==============================================================================
# TV3 — Outbox Pattern + Event-Driven — Demo data seeder & scenario runner
#
# Tạo bộ dữ liệu (câu hỏi, bài thi đã publish, phiên làm bài in_progress) rồi
# (tuỳ chọn) tự chạy 3 kịch bản demo mô tả trong TV3_walkthrough.md / DEMO.md:
#   1. Happy path            — nộp bài, outbox PENDING -> SENT trong ~2s
#   2. RabbitMQ down         — nộp bài khi RabbitMQ down, event không mất
#   3. Statistics down       — nộp bài khi Statistics down, message chờ trong queue
#
# Yêu cầu chạy tại thư mục gốc repo (nơi có docker-compose.yml) vì script dùng
# `docker compose exec/stop/start/logs` để thao tác trực tiếp lên các service.
#
# Cách dùng:
#   bash scripts/tv3-seed-demo-data.sh seed        # chỉ tạo dữ liệu, không đụng docker
#   bash scripts/tv3-seed-demo-data.sh scenario1   # seed + chạy kịch bản 1
#   bash scripts/tv3-seed-demo-data.sh scenario2   # seed + chạy kịch bản 2
#   bash scripts/tv3-seed-demo-data.sh scenario3   # seed + chạy kịch bản 3
#   bash scripts/tv3-seed-demo-data.sh all         # seed + cả 3 kịch bản tuần tự
#
# Biến môi trường có thể override (khớp .env.example):
#   GATEWAY_PORT, EXAM_SERVICE_PORT, SUBMISSION_SERVICE_PORT,
#   STATISTICS_SERVICE_PORT, RABBITMQ_UI_PORT, RABBITMQ_USER, RABBITMQ_PASS,
#   DB_PASSWORD
# ==============================================================================

set -euo pipefail

# ─── Cấu hình ────────────────────────────────────────────────────────────────
GATEWAY_PORT="${GATEWAY_PORT:-8080}"
EXAM_SERVICE_PORT="${EXAM_SERVICE_PORT:-5001}"
SUBMISSION_SERVICE_PORT="${SUBMISSION_SERVICE_PORT:-5002}"
STATISTICS_SERVICE_PORT="${STATISTICS_SERVICE_PORT:-5003}"
RABBITMQ_UI_PORT="${RABBITMQ_UI_PORT:-15672}"
RABBITMQ_USER="${RABBITMQ_USER:-admin}"
RABBITMQ_PASS="${RABBITMQ_PASS:-changeme}"
DB_PASSWORD="${DB_PASSWORD:-123456}"

GATEWAY_URL="http://localhost:${GATEWAY_PORT}/api"
TEACHER_ID="teacher_tv3_demo"
RUN_TAG="$(date +%s)"                       # để mỗi lần chạy có studentId/exam khác nhau
STATE_DIR="$(mktemp -d)"
trap 'rm -rf "$STATE_DIR"' EXIT

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

# ─── Helpers ─────────────────────────────────────────────────────────────────
log()  { echo -e "\n\033[1;36m▶ $*\033[0m" >&2; }
ok()   { echo -e "\033[1;32m  ✓ $*\033[0m" >&2; }
warn() { echo -e "\033[1;33m  ⚠ $*\033[0m" >&2; }
die()  { echo -e "\033[1;31m✗ $*\033[0m" >&2; exit 1; }

for bin in curl jq docker; do
  command -v "$bin" >/dev/null 2>&1 || die "Thiếu lệnh '$bin'. Cài đặt rồi chạy lại."
done

wait_for() {
  local name="$1" url="$2" tries=60
  log "Chờ ${name} sẵn sàng (${url}) ..."
  until curl -s -o /dev/null -w '%{http_code}' "$url" 2>/dev/null | grep -qE '^(200|204)$'; do
    tries=$((tries - 1))
    [ "$tries" -le 0 ] && die "${name} không phản hồi sau 60s. Kiểm tra 'docker compose ps'."
    sleep 1
  done
  ok "${name} đã sẵn sàng"
}

create_question() {
  # $1 content  $2 A  $3 B  $4 C  $5 D  $6 correct(A/B/C/D)
  curl -s -X POST "${GATEWAY_URL}/questions" \
    -H 'Content-Type: application/json' \
    -d @- <<EOF | jq -r '.data.id // .id'
{
  "content": "$1",
  "optionA": "$2",
  "optionB": "$3",
  "optionC": "$4",
  "optionD": "$5",
  "correctAnswer": "$6",
  "createdBy": "${TEACHER_ID}"
}
EOF
}

create_exam() {
  # $1 title  $2 durationMinutes  $3 json-array question ids
  curl -s -X POST "${GATEWAY_URL}/exams" \
    -H 'Content-Type: application/json' \
    -d @- <<EOF | jq -r '.data.id // .id'
{
  "title": "$1",
  "description": "Dữ liệu demo TV3 — Outbox Pattern (tự động tạo bởi tv3-seed-demo-data.sh)",
  "durationMinutes": $2,
  "questionIds": $3,
  "createdBy": "${TEACHER_ID}"
}
EOF
}

publish_exam() {
  curl -s -X PATCH "${GATEWAY_URL}/exams/$1/status" \
    -H 'Content-Type: application/json' \
    -d '{"status":"published"}' >/dev/null
}

start_submission() {
  # $1 examId  $2 studentId  ->  in stdout: submissionId
  curl -s -X POST "${GATEWAY_URL}/submissions" \
    -H 'Content-Type: application/json' \
    -d "{\"examId\":\"$1\",\"studentId\":\"$2\"}" | jq -r '.data.submissionId // .submissionId'
}

save_random_answers() {
  # $1 submissionId  $2 json-array question ids
  local ids="$2"
  local answers
  answers=$(jq -c 'to_entries | map({questionId: .value, selectedOption: (["A","B","C","D"][.key % 4])})' <<<"$ids")
  curl -s -X PUT "${GATEWAY_URL}/submissions/$1/answers" \
    -H 'Content-Type: application/json' \
    -d "{\"answers\": ${answers}}" >/dev/null
}

# ─── Seed: ngân hàng câu hỏi dùng chung cho cả 3 kịch bản ───────────────────
seed_question_bank() {
  log "Tạo ngân hàng câu hỏi (5 câu, giáo viên=${TEACHER_ID})"
  local ids=()
  ids+=("$(create_question "TV3 demo — RabbitMQ dùng để làm gì trong Outbox Pattern?" "Lưu điểm học sinh" "Publish event từ outbox_events sang Statistics" "Thay thế MySQL" "Xác thực người dùng" "B")")
  ids+=("$(create_question "TV3 demo — OutboxRelayJob chạy theo chu kỳ bao lâu?" "Mỗi 200ms" "Mỗi 2 giây" "Mỗi 2 phút" "Chỉ chạy 1 lần khi khởi động" "B")")
  ids+=("$(create_question "TV3 demo — Nếu Statistics Service down khi publish, message nằm ở đâu?" "Bị xoá khỏi hệ thống" "Nằm chờ trong queue RabbitMQ (durable)" "Quay lại bảng submissions" "Ghi log rồi bỏ qua" "B")")
  ids+=("$(create_question "TV3 demo — Vì sao publish RabbitMQ KHÔNG nằm trong transaction lưu điểm?" "Vì RabbitMQ không hỗ trợ transaction" "Để tránh giữ transaction DB mở lâu / rollback điểm khi MQ down" "Vì không cần thiết" "Vì Statistics không cần biết" "B")")
  ids+=("$(create_question "TV3 demo — Bảng nào lưu lại các event đang chờ publish?" "submissions" "outbox_events" "processed_events" "exam_questions" "B")")
  QUESTION_IDS_JSON=$(printf '%s\n' "${ids[@]}" | jq -R . | jq -sc .)
  echo "$QUESTION_IDS_JSON" > "${STATE_DIR}/question_ids.json"
  ok "Đã tạo ${#ids[@]} câu hỏi: $(jq -c . <<<"$QUESTION_IDS_JSON")"
}

# ─── Seed: 1 bài thi published + 1 phiên làm bài in_progress (đã lưu đáp án) ─
seed_exam_and_session() {
  # $1 scenario-label (vd: scenario1)  ->  in stdout: "examId submissionId"
  local label="$1"
  local qids; qids="$(cat "${STATE_DIR}/question_ids.json")"
  local exam_id student_id submission_id

  exam_id="$(create_exam "TV3 Demo - ${label} - ${RUN_TAG}" 30 "$qids")"
  [ -n "$exam_id" ] && [ "$exam_id" != "null" ] || die "Tạo bài thi thất bại cho ${label}"
  publish_exam "$exam_id"

  student_id="student_tv3_${label}_${RUN_TAG}"
  submission_id="$(start_submission "$exam_id" "$student_id")"
  [ -n "$submission_id" ] && [ "$submission_id" != "null" ] || die "Tạo phiên làm bài thất bại cho ${label}"
  save_random_answers "$submission_id" "$qids"

  ok "${label}: exam=${exam_id} student=${student_id} submission=${submission_id} (status=in_progress, đáp án đã lưu)"
  echo "${exam_id} ${student_id} ${submission_id}"
}

# ─── docker compose helpers ──────────────────────────────────────────────────
dc()      { docker compose "$@"; }
sql_submission() { dc exec -T submission-db mysql -uroot -p"${DB_PASSWORD}" -N -B -e "$1" submission_db; }

# ─── Kịch bản 1 — Happy path ─────────────────────────────────────────────────
run_scenario1() {
  log "KỊCH BẢN 1 — Happy path"
  read -r exam_id student_id submission_id <<<"$(seed_exam_and_session "scenario1")"

  log "Nộp bài (POST /submissions/${submission_id}/submit)"
  curl -s -X POST "${GATEWAY_URL}/submissions/${submission_id}/submit" -H 'Content-Type: application/json' | jq .

  log "Đợi 3s để OutboxRelayJob publish, rồi kiểm tra outbox_events"
  sleep 3
  sql_submission "SELECT id, event_type, status, retry_count FROM outbox_events WHERE payload LIKE '%${submission_id}%';" || warn "Không đọc được outbox_events — kiểm tra container submission-db"

  log "Log statistics-service (30 dòng cuối)"
  dc logs statistics-service --tail=30 | grep -i "${submission_id}" || warn "Chưa thấy submissionId trong log — đợi thêm vài giây rồi xem: docker compose logs statistics-service --tail=50"
  ok "Kịch bản 1 hoàn tất — kỳ vọng status=SENT và log Statistics đã nhận bài nộp mới"
}

# ─── Kịch bản 2 — RabbitMQ down khi nộp bài ─────────────────────────────────
run_scenario2() {
  log "KỊCH BẢN 2 — RabbitMQ down khi nộp bài"
  read -r exam_id student_id submission_id <<<"$(seed_exam_and_session "scenario2")"

  log "Dừng RabbitMQ"
  dc stop rabbitmq

  log "Nộp bài trong lúc RabbitMQ down (kỳ vọng vẫn nộp thành công, chỉ publish bị hoãn)"
  curl -s -X POST "${GATEWAY_URL}/submissions/${submission_id}/submit" -H 'Content-Type: application/json' | jq .

  sql_submission "SELECT id, event_type, status, retry_count FROM outbox_events WHERE payload LIKE '%${submission_id}%';" || warn "Không đọc được outbox_events"
  ok "-> Row trên phải có status=PENDING (event đã ghi DB, chưa publish được vì MQ down)"

  log "Khởi động lại RabbitMQ, chờ outbox_events tự chuyển sang SENT (tối đa 30s)"
  dc start rabbitmq

  local tries=30
  local status="PENDING"
  while [ "$tries" -gt 0 ]; do
    sleep 1
    status="$(sql_submission "SELECT status FROM outbox_events WHERE payload LIKE '%${submission_id}%' LIMIT 1;" 2>/dev/null || echo "")"
    [ "$status" = "SENT" ] && break
    tries=$((tries - 1))
  done

  sql_submission "SELECT id, event_type, status, retry_count FROM outbox_events WHERE payload LIKE '%${submission_id}%';" || true

  if [ "$status" = "SENT" ]; then
    ok "Kịch bản 2 hoàn tất — status đã tự chuyển sang SENT, không cần thao tác tay"
  else
    warn "Sau 30s vẫn chưa SENT — kiểm tra log submission-service để xem OutboxRelayJob có lỗi khác không"
  fi
}

# ─── Kịch bản 3 — Statistics Service down khi event được publish ───────────
run_scenario3() {
  log "KỊCH BẢN 3 — Statistics Service down khi event được publish"
  read -r exam_id student_id submission_id <<<"$(seed_exam_and_session "scenario3")"

  log "Dừng statistics-service"
  dc stop statistics-service

  log "Nộp bài trong lúc Statistics down (Outbox vẫn publish được sang RabbitMQ)"
  curl -s -X POST "${GATEWAY_URL}/submissions/${submission_id}/submit" -H 'Content-Type: application/json' | jq .

  sleep 3
  sql_submission "SELECT id, event_type, status, retry_count FROM outbox_events WHERE payload LIKE '%${submission_id}%';" || true
  ok "-> Row trên phải có status=SENT dù Statistics đang down"

  log "Kiểm tra message đang chờ trong queue statistics.exam-submitted (RabbitMQ Management API)"
  dc exec -T rabbitmq rabbitmqctl list_queues name messages messages_ready consumers \
    | grep -E "^name|statistics.exam-submitted" \
    || warn "Không đọc được queue qua rabbitmqctl"

  log "Khởi động lại statistics-service, chờ service healthy rồi kiểm tra xử lý tồn đọng"
  dc start statistics-service

  tries=30
  until curl -s -o /dev/null -w '%{http_code}' "http://localhost:${STATISTICS_SERVICE_PORT}/health" 2>/dev/null | grep -qE '^(200|204)$'; do
    tries=$((tries - 1))
    [ "$tries" -le 0 ] && { warn "statistics-service không healthy sau 30s"; break; }
    sleep 1
  done

  log "Đợi thêm 3s để consumer kịp tiêu thụ message tồn đọng"
  sleep 3

  dc exec -T rabbitmq rabbitmqctl list_queues name messages messages_ready consumers \
    | grep -E "^name|statistics.exam-submitted" || warn "Không đọc được queue qua rabbitmqctl"

  dc logs statistics-service --tail=30 | grep -i "${submission_id}" || warn "Chưa thấy log xử lý — thử: docker compose logs statistics-service --tail=50"
  ok "Kịch bản 3 hoàn tất — kỳ vọng Statistics tự nhận và xử lý hết message tồn đọng sau khi khởi động lại"
}

seed_only() {
  log "Chỉ tạo dữ liệu (không đụng tới trạng thái docker của các service)"
  seed_question_bank
  for label in scenario1 scenario2 scenario3; do
    seed_exam_and_session "$label" >/dev/null
  done
  echo
  ok "Đã tạo xong bộ dữ liệu demo TV3. Mỗi bài thi có 1 phiên làm bài (status=in_progress, đáp án đã lưu)."
  echo "  Dùng 'bash scripts/tv3-seed-demo-data.sh scenario1|scenario2|scenario3|all' để tự chạy kịch bản kèm tạo dữ liệu."
}

# ─── Main ─────────────────────────────────────────────────────────────────
main() {
  local mode="${1:-seed}"

  wait_for "Gateway"            "http://localhost:${GATEWAY_PORT}/health"
  wait_for "Exam Service"       "http://localhost:${EXAM_SERVICE_PORT}/actuator/health"
  wait_for "Submission Service" "http://localhost:${SUBMISSION_SERVICE_PORT}/health"
  wait_for "Statistics Service" "http://localhost:${STATISTICS_SERVICE_PORT}/health"

  seed_question_bank

  case "$mode" in
    seed)
      for label in scenario1 scenario2 scenario3; do
        seed_exam_and_session "$label" >/dev/null
      done
      ok "Đã tạo xong bộ dữ liệu demo TV3 (3 bài thi published + 3 phiên in_progress)."
      ;;
    scenario1) run_scenario1 ;;
    scenario2) run_scenario2 ;;
    scenario3) run_scenario3 ;;
    all)
      run_scenario1
      run_scenario2
      run_scenario3
      ;;
    *)
      die "Mode không hợp lệ: '${mode}'. Dùng: seed | scenario1 | scenario2 | scenario3 | all"
      ;;
  esac

  echo -e "\n\033[1;32m🎉 Xong.\033[0m"
}

main "$@"