# KIS 종목 마스터와 OpenDART 배치 인수인계

## 기준 상태

2026-11-25, `main` 최초 커밋 `1a45880` 이후 로컬 변경. 요구사항과 검증 항목은 [작업 목록](../04_task.md)에 정리했다.

## 완료한 작업

- Gradle 도구 체인을 Java 17에서 21로 맞추고 Spring Boot 4.2.0, Spring Batch 6.1.0, JDBC JobRepository 및 Flyway를 연결했다.
- KIS 다섯 시장의 ZIP 고정, 청크 체크포인트, 검증과 시장별 공개를 연결했다. 스냅샷·변경 이력·최신 참조·반영 완료 상태를 같은 트랜잭션에서 확정한다.
- OpenDART 두 시장의 접수일별 페이지 검증, 조회 리비전 내부의 새 조회 차수, 접수번호 중복 제거와 완료 건수 복구를 연결했다. 시장·날짜·리비전별 조회와 재개 명령을 제공한다.
- 기본 JDBC URL에 `rewriteBatchedStatements=true`를 적용하고 KIS 기본 청크 500을 유지했다. 로컬 MySQL 8.4용 `compose.yml`을 추가했다.
- KIS 공식 다운로드 기본 주소와 `Market`의 파일명으로 URL을 조합한다. 통합 시험은 공통 주소만 로컬 HTTP 서버로 교체하고, 자식 JVM 종료 후 MySQL 잠금 해제를 확인한 뒤 재개한다.
- OpenDART는 공식 `list.json`과 접수일 내림차순을 코드에 고정했다. API 키는 `DART_API_KEY`에서 읽고, 로컬 엔드포인트는 테스트 코드에서만 주입한다.
- `kis`와 `dart`에 공개 API와 내부 구현 경계를 두고, CLI와 스케줄러가 두 API를 호출하게 했다. 시장·실행 모델과 공통 Job 실행·복구를 `shared`에 뒀다.
- 두 Job은 시장·기준일·리비전의 식별 `JobParameters`를 실행당 한 번 `@JobScope`의 `@JobParameter`로 바인딩하고 Step에서 공유한다.
- `COLLECTION_RUN_ON_STARTUP=true`를 설정하면 앱 시작 직후 일일 수집과 같은 경로를 한 번 실행할 수 있다.
- `ClockConfig`가 한국 시간의 `Clock`을 제공한다. 실행일 계산과 cron 등록이 같은 빈의 시간대를 사용한다.
- 일일 수집을 Quartz로 예약한다. MySQL JDBC JobStore는 Flyway V005에서 관리하며, 예약과 기동 시 1회 수집은 기존 Spring Batch 시작·복구 흐름을 사용한다.
- KIS와 OpenDART의 내부 패키지를 `job`, `step`, `processing`으로 나눴다. Job은 파라미터와 순서를 정의하고 Step은 트랜잭션을 구성한 뒤 처리 코드에 위임한다.

## 확정한 결정

- 기존 애플리케이션 루트 패키지와 YAML 경로를 유지했다. 미국 세 시장은 실제 파일에서 헤더 없는 탭 구분 24열과 대문자 ZIP 엔트리를 확인했다. KIS 원천 효력일은 공식 ZIP 응답에서 확인되지 않아 미확인으로 기록했다.
- 단위 시험은 외부 서비스 없이 실행한다. 통합 시험은 명시한 전용 MySQL 스키마와 로컬 HTTP를 사용한다. 스케줄은 운영 기준 확인 전에는 기본 비활성이다.

## 검증

| 명령 또는 확인 방법 | 실제 결과 | 대상 |
| --- | --- | --- |
| `./gradlew dependencyInsight --dependency spring-batch-core --configuration runtimeClasspath` | Spring Batch 6.1.0 선택 확인 | Java 21, Boot 4.2.0 |
| `./gradlew clean build` | 시험 23건과 패키지 빌드 통과 | MySQL 8.4.13, JDBC 배치 재작성, 로컬 HTTP, 별도 JVM 강제 종료 |
| `docker compose config --quiet` 및 `docker compose up -d --wait mysql` | 설정 검증과 컨테이너 기동 확인. 앱 기본 URL로 접속해 드라이버 옵션 및 청크 500 확인 | Compose MySQL 8.4.10 |
| URL 리팩토링 후 `./gradlew clean build` 및 배포 JAR의 `kis resume` | 강제 종료 복구 포함 시험 23건 통과. 공식 기본 주소로 받은 코스피 ZIP에서 2,471건 처리 | Compose MySQL 8.4.10 |
| OpenDART 설정 정리 후 `./gradlew clean build` 및 배포 클래스 설정 확인 | 시험 23건 통과. 운영 엔드포인트와 환경 변수 키 바인딩 확인 | Compose MySQL 8.4.10 |
| 모듈 분리 후 `./gradlew clean build` 및 배포 JAR의 운영 명령 확인 | 경계 검증과 강제 종료 복구 포함 시험 24건 통과. `kis resume`, `kis status`, `dart status` 확인 | Compose MySQL 8.4.10, 로컬 HTTP |
| JobParameters 바인딩 정리 후 `./gradlew clean build` | 일일 수집의 시장·기준일·리비전 메타데이터와 강제 종료 복구 포함 시험 24건 통과 | Compose MySQL 8.4.10 |
| 기동 시 1회 수집 구현 후 `./gradlew clean build` | 실행 인자 없는 기동에서 수집이 한 번만 실행되는 경우를 포함해 시험 25건 통과 | Compose MySQL 8.4.10, 로컬 HTTP |
| ClockConfig 분리 후 `./gradlew clean build` | 한국 시간 19:30이 UTC 10:30으로 등록되는 경우를 포함해 시험 26건 통과 | Compose MySQL 8.4.10 |
| Quartz 도입 후 `./gradlew clean build`, 로컬 Checkstyle 및 배포 JAR 확인 | 시험 26건 통과. JDBC 트리거 보존·미스파이어 정책·Quartz를 통한 JobParameters 전달 확인 | Compose MySQL 8.4.10 |
| 패키지 분리 전후 복구 시험, 분리 후 `./gradlew clean build` 및 로컬 Checkstyle | 시험 26건과 Checkstyle 통과. 이전 JAR이 첫 청크 2건을 커밋한 뒤 실패한 실행을 새 JAR이 같은 JobInstance에서 2건부터 이어 처리 | Compose MySQL 8.4.10, 로컬 HTTP |
| 배포 JAR에서 시장별 `kis resume` 실행 | 코스피 2,471건, 코스닥 1,838건, 나스닥 5,232건, 뉴욕 2,524건, 아멕스 4,754건 공개 | 공식 KIS ZIP, 별도 MySQL 시험 스키마 |

## 남은 질문과 다음 작업

- T036의 OpenDART 실키 연결, 여러 수집일의 관찰 및 원본 보존 기간 확정이 남아 있다. 후보 실행값과 통합 시험 재현 방법은 [작업 목록](../04_task.md#10-실행과-복구)에 있다.
