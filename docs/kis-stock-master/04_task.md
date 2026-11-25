# 구현 작업 목록 — 종목 마스터 동기화와 국내 주요 공시 수집

작성일 `2026-11-25` · 입력 [기능 명세](spec.md) · [구현 계획](plan.md) · [PRD](PRD.md)

## 1. 프로젝트 준비

경로는 구현할 Spring Batch 애플리케이션 루트 기준이다. `src/main/java/batch/`와 `src/test/java/batch/`는 제안 패키지 경로이며, T001에서 확인한 프로젝트 규칙에 맞춰 적용한다. `[US1]`부터 `[US4]`까지는 기능 명세의 US-01부터 US-04까지를 가리킨다. `[P]`는 같은 단계의 선행 작업이 끝난 뒤 병행할 수 있는 작업이다.

- [ ] T001 단일 Java 애플리케이션의 루트와 빌드 도구를 정하고, 계획의 Java·Spring Boot·Spring Batch·JDBC 기준으로 빌드 설정과 `src/main/java/batch/BatchApplication.java`를 준비한다.
- [ ] T002 DB 연결, 원본 보관 경로, KIS 다운로드 URL, OpenDART 키와 한국 시간 설정을 `src/main/resources/application.yml`에서 받는다. KIS 건수 감소 한도, 공시 페이지 크기·정렬과 요청 재시도 횟수·지연의 설정 항목 및 통합 시험 고정값은 T007·T012·T022·T024·T025에 앞서 `src/test/resources/application-test.yml`과 함께 정한다.

## 2. 공통 배치 기반

- [ ] T003 `src/main/java/batch/config/BatchInfrastructure.java`와 `src/main/resources/db/migration/V001__spring_batch_metadata.sql`에서 MySQL 배치 메타데이터를 준비하고, JDBC `JobRepository`와 업무 적재에 같은 데이터소스와 트랜잭션 관리자를 연결한다.
- [ ] T004 [P] `src/test/java/batch/support/BatchIntegrationSupport.java`에서 실제 MySQL, 로컬 HTTP 응답, 청크·공개 커밋 전후 실패 주입을 준비한다. 강제 종료 시험은 별도 JVM을 사용해 조회 중이나 커밋 직후에 실행을 중단하고, 같은 DB와 저장 입력으로 재개할 수 있게 한다. 시험은 실제 API 키 없이 실행한다.
- [ ] T005 [P] `src/main/java/batch/storage/BatchInputStore.java`에서 보관 위치와 검증 정보를 기록하고, 실행이 다시 시작되면 저장된 입력을 읽을 수 있게 한다.

## 3. US-01 · 한국투자증권 종목 마스터의 첫 정상본 (P1)

**독립 검증** — 로컬 HTTP에서 다섯 ZIP을 받아 공개하고, 시장별 종목과 처리 건수, 마지막 정상 수집 시각을 조회한다. 정상 파일의 주식·ETF·ETN과 원본 코드가 시험본과 일치해야 한다. 검증 실패 시장은 마지막 정상본을 유지한다. `FR-001`, `FR-002`, `FR-003`, `FR-004`, `SC-001`

### 시험

- [ ] T006 [P] [US1] `src/test/resources/fixtures/kis/`와 `src/test/java/batch/kis/KisMasterParserTest.java`에 코스피·코스닥 개별 레이아웃, 미국 세 시장 공통 레이아웃, 한글·선행 0, 주식·ETF·ETN을 포함한 시험본과 예상 행을 준비한다.
- [ ] T007 [P] [US1] `src/test/java/batch/kis/MarketMasterImportJobTest.java`에 다섯 시장 정상 수집, 다른 시장의 동일 심볼, 최초 정상본 미확보, 손상 ZIP·누락 필드·중복 심볼·빈 파일·건수 급감의 공개 차단을 검증하는 시험을 작성한다. 원천 효력일이 있는 시험본은 수집 시각과 구분해 반환하고, 효력일이 없는 시험본은 미확인으로 표시하는지 확인한다.

### 구현

- [ ] T008 [US1] `src/main/resources/db/migration/V002__kis_master.sql`에 원천 효력일을 선택 필드로 가진 회차·시장 입력, 임시 행, 시장 상태, 공개 스냅샷과 종목을 정의한다. 임시 행은 입력과 원본 행 번호, 공개 스냅샷은 입력, 공개 종목은 `(snapshot_id, market, symbol)` 조합으로 고유하게 유지한다.
- [ ] T009 [P] [US1] `src/main/java/batch/kis/DownloadArchiveTasklet.java`에 다섯 시장 ZIP 다운로드와 압축·내부 파일명 검증을 구현한다. 원본을 확정한 뒤 경로·해시·파서 규격·수집 시각을 저장하고, 확인 가능한 원천 효력일을 별도로 기록한다.
- [ ] T010 [P] [US1] `src/main/java/batch/kis/parser/KospiParser.java`, `src/main/java/batch/kis/parser/KosdaqParser.java`, `src/main/java/batch/kis/parser/UsMarketParser.java`를 구현한다. 실제 파일의 헤더·열 수를 확인해 시험본을 보완하고 원본 유형·상품 코드를 문자열로 보존한다.
- [ ] T011 [US1] `src/main/java/batch/kis/InstrumentStageStep.java`에서 확정 입력을 청크로 읽고 파싱한 행을 저장한다. 청크 커밋과 읽기 위치를 함께 검증할 수 있게 하고, 재실행한 같은 원본 행과 서로 다른 행에 나온 동일 종목을 구분한다.
- [ ] T012 [US1] `src/main/java/batch/kis/ValidateSnapshotTasklet.java`에 필수값, 지원 유형, 시장 내 중복, 빈 결과와 T002에서 정한 건수 감소 검증을 구현한다. 검사 건수와 실패 사유는 업무 롤백 후에도 조회할 수 있게 한다.
- [ ] T013 [US1] `src/main/java/batch/kis/PublishSnapshotTasklet.java`에서 시장 상태 행 잠금 아래 검증 기준을 다시 적용하고, 공개 스냅샷·정상본 참조·수집 시각·반영 완료 표식을 한 트랜잭션으로 갱신한다. 첫 정상본은 비교 기준으로 확정한다. 변경 이력 연동은 T035에서 마친다.
- [ ] T014 [US1] `src/main/java/batch/kis/MarketMasterImportJob.java`에 다운로드 → 임시 적재 → 검증 → 공개 Step을 연결한다. `market`, `collectionDate`, `inputRevision`으로 시장 실행을 식별한다.
- [ ] T015 [US1] `src/main/java/batch/kis/MarketMasterCycleRunner.java`와 `src/main/java/batch/kis/MarketMasterQueries.java`에서 다섯 시장을 순차 실행하고 시장별 결과를 집계한다. 한 시장이 실패해도 다음 시장이 이어서 실행된다. 최신 정상 목록·정상 수집 시각·원천 효력일·처리 건수·검증 결과·마지막 시도 오류를 운영 조회에 제공한다.

## 4. US-02 · 종목 마스터 실패 후 재실행 (P1)

**검증 순서** — 한 시장에 실패를 주입해 나머지 네 시장의 정상 공개를 확인한다. 실패 시장을 재시작한 뒤 입력 해시, Job 실행 기록, 임시 행 수와 최신 정상본을 대조한다. 변경 이력까지 포함하는 최종 인수 검증은 T035의 연동 후 T030으로 마친다. `FR-004`, `FR-005`, `SC-002`

### 시험

- [ ] T016 [P] [US2] `src/test/java/batch/kis/MarketMasterRestartTest.java`에 원본 확보 전 실패, 청크 커밋 후 중단과 `STARTED` 복구, 네 시장 성공 후 한 시장 재개, 수정 원본을 새 리비전으로 처리하는 시험을 작성한다. 운영 명령으로 실패 단계와 원본 식별 정보를 찾고 같은 입력으로 재개할 수 있는지 확인한다.
- [ ] T017 [P] [US2] `src/test/java/batch/kis/MarketMasterPublishRecoveryTest.java`에 공개 커밋 전 롤백, 공개 커밋 후 Step 완료 기록 전 중단, 더 최신 정상본이 공개된 뒤 이전 실행이 재개되는 시험을 작성한다.

### 구현

- [ ] T018 [US2] `src/main/java/batch/kis/MarketMasterCycleRunner.java`와 `src/main/java/batch/kis/MarketMasterRestartService.java`에서 성공 시장을 건너뛰고 실패 시장의 기존 `JobInstance`를 재시작한다. `src/main/java/batch/kis/MarketMasterOperationsCommand.java`에서 시장·수집 기준일·입력 리비전으로 Job 실행 ID, 원본 참조·해시, 상태와 실패 단계를 조회하고 재개한다. `STARTED`는 이전 프로세스 종료와 체크포인트 확인 후 복구한다.
- [ ] T019 [P] [US2] `src/main/java/batch/kis/DownloadArchiveTasklet.java`와 `src/main/java/batch/kis/InstrumentStageStep.java`에서 같은 입력의 고정 원본·파서 규격·마지막 커밋 위치를 재사용한다. 원본 교체는 새 `inputRevision`에서 처음부터 읽도록 연결한다.
- [ ] T020 [P] [US2] `src/main/java/batch/kis/PublishSnapshotTasklet.java`에서 커밋된 입력의 반영 완료 표식을 재사용하고, 시장 잠금 아래 수집 기준일과 입력 리비전을 비교해 늦은 이전 실행을 운영 결과로만 기록한다.

## 5. US-04 · OpenDART 코스피·코스닥 주요사항보고 (P1)

**독립 검증** — 여러 페이지의 양 시장 응답과 정정 공시를 읽어 접수번호 집합을 시험본과 대조한다. 같은 날짜 재조회, 중간 페이지 실패, 0건 성공과 완료 페이지 재사용을 각각 검증한다. `FR-007`, `FR-008`, `FR-009`, `SC-003`, `SC-004`

### 시험

- [ ] T021 [P] [US4] `src/test/resources/fixtures/dart/`와 `src/test/java/batch/dart/DartDisclosureImportJobTest.java`에 시장별 전체 페이지, 원본·정정의 별도 접수번호, 마스터에 없는 종목, 완료 후 새 리비전에서 반복된 접수번호와 뒤늦게 추가된 공시를 준비하고 조회 결과를 대조한다.
- [ ] T022 [P] [US4] `src/test/java/batch/dart/DartDisclosureRestartTest.java`에 첫 페이지 0건, 중간 페이지 실패·빈 응답, 페이지 수가 같을 때의 페이지 간 접수번호 중복, 총 건수 불일치, T002의 재시도 경계와 공개 커밋 전후 실패 시험을 작성한다. 페이지 조회 중과 공개 커밋 직후의 별도 JVM 강제 종료, 이전 프로세스가 살아 있는 경우 복구 거부와 자동 재조회 범위 밖 날짜의 지정 복구도 시험한다.

### 구현

- [ ] T023 [US4] `src/main/resources/db/migration/V003__dart_disclosure.sql`에 `(receipt_date, market, scan_revision)`별 수집 시도, 조회 차수·페이지 번호별 저장 영역과 검증 완료 묶음 참조를 정의한다. 공개 공시의 기본 키는 문자열 `rcept_no`로 둔다.
- [ ] T024 [P] [US4] `src/main/java/batch/dart/OpenDartClient.java`에서 `list.json`의 시작일과 종료일을 같은 접수일로 지정한다. 시장별 `corp_cls=Y` 또는 `K`, `pblntf_ty=B`, `last_reprt_at=N`, 페이지 번호와 고정한 페이지 크기·정렬을 전달한다. 인증키는 실행 설정에서 읽고 T002의 정책대로 타임아웃·일시적 서버 오류에만 제한 재시도한다. [OpenDART 공시검색 요청·응답](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019001)
- [ ] T025 [P] [US4] `src/main/java/batch/dart/FetchDisclosurePagesTasklet.java`에서 페이지 순서, 페이지별 총 페이지 수·총 건수, 시장·접수일·접수번호를 검증한다. 같은 묶음의 접수번호 중복이나 고유 접수번호 수 불일치는 미완료로 남긴다. 첫 페이지 0건을 별도 처리하고 실패 상태는 업무 롤백 후에도 조회할 수 있게 한다. 완료 표식이 있으면 묶음을 재사용하고, 없으면 같은 리비전의 새 조회 차수에서 첫 페이지부터 읽는다. [공시 페이지 완료 판정](plan.md#4-opendart-주요사항보고-수집-job)
- [ ] T026 [P] [US4] `src/main/java/batch/dart/PublishDisclosuresTasklet.java`에서 검증된 묶음의 공시만 접수번호로 중복 없이 적재한다. 공시 행·신규 건수·수집 시도 완료 표식을 같은 트랜잭션에 기록하고, 완료된 동일 시도의 결과를 재사용한다.
- [ ] T027 [US4] `src/main/java/batch/dart/DartDisclosureImportJob.java`와 `src/main/java/batch/dart/DartDisclosureCycleRunner.java`에 조회·검증 → 공개 Step을 연결한다. `src/main/java/batch/dart/DartDisclosureOperationsCommand.java`에서 시장·접수일·조회 리비전으로 실행 상태를 조회하고 일반 실패와 `STARTED`를 복구한다. 강제 종료는 이전 프로세스의 종료를 확인하고 같은 리비전으로 복구한다. 자동 재조회 범위 밖도 지정할 수 있고 완료된 날짜의 명시적 재조회에는 새 리비전을 쓴다. [공시 Job 복구 조건](plan.md#4-opendart-주요사항보고-수집-job)
- [ ] T028 [P] [US4] `src/main/java/batch/dart/DartDisclosureQueries.java`에서 시장·접수일별 상태, 실패 이유, 신규 건수와 공시의 접수번호·접수일·시장·종목코드·회사명·제목·원문 링크를 조회할 수 있게 한다.

## 6. US-03 · 종목 변경 이력 (P2)

**독립 검증** — 최초 정상본, 동일 내용의 다음 정상본, 변동이 있는 연속 정상본을 순서대로 반영한다. 시장·일자별 변경의 전후 값과 공개된 스냅샷을 대조한다. `FR-005`, `FR-006`, `SC-002`

### 시험

- [ ] T029 [P] [US3] `src/test/java/batch/kis/MarketMasterChangeHistoryTest.java`에 최초·동일 정상본에서 이력 0건, 신규·이름 변경·국내 거래정지 상태 변경·목록 미확인·재등장과 실패 스냅샷의 이력 미공개를 검증하는 시험을 작성한다.
- [ ] T030 [P] [US3] `src/test/java/batch/kis/MarketMasterPublishRecoveryTest.java`에 종목명 수정과 신규 종목을 포함한 정상 입력을 준비한다. 업무 쓰기를 마친 커밋 직전과 공개 커밋 직후 Step 완료 기록 전에 각각 중단한다. 같은 입력 재개 후 입력당 공개 스냅샷 1건, 최신 참조, 종목 집합, 이력 키와 전후 값, 반영 표식을 중단 없는 실행과 대조한다. T035 연동 후 이 시험을 통과하면 US-02의 변경 이력 복구 검증을 마친다.

### 구현

- [ ] T031 [US3] `src/main/resources/db/migration/V004__kis_change_history.sql`에 공개 스냅샷에 연결되는 변경 이력을 정의한다. `(published_snapshot_id, market, symbol, change_type, field)`를 고유 키로 두고 필드 비교가 없는 변경에도 일정한 값을 사용한다.
- [ ] T032 [P] [US3] `src/main/java/batch/kis/MarketMasterDiff.java`에서 현재 검증본과 직전 정상본을 비교한다. 이전 공개 기록을 사용해 신규와 재등장을 구분하고, 제공되는 국내 상태값을 비교한다.
- [ ] T033 [P] [US3] `src/main/java/batch/kis/MarketMasterChangeQueries.java`에 시장·날짜별 변경 이력, 종목 유형, 전후 값과 수집 시각 조회를 구현한다.
- [ ] T034 [US3] `src/main/java/batch/kis/MarketMasterChangeRepository.java`에서 변경 결과를 고유 키와 함께 적재하고 공개 스냅샷에 연결한다.
- [ ] T035 [US3] `src/main/java/batch/kis/PublishSnapshotTasklet.java`에서 시장 잠금 이후 변경을 계산하고 공개 스냅샷·이력·최신 참조·정상 수집 시각·반영 표식을 하나의 트랜잭션에서 확정한다. T020의 재실행·역순 방지와 연결한 뒤 T030을 검증한다.

## 7. 일일 실행과 통합 확인

- [ ] T036 실제 입력을 점검해 일일 실행 시각, 원본 보존 기간, 시장별 건수 감소 한도, 공시 재조회 범위와 재시도 운영값을 확정하고 `src/main/resources/application.yml`에 반영한다. 외부 연결 점검은 로컬 통합 시험과 분리한다.
- [ ] T037 `src/main/java/batch/schedule/DailyCollectionScheduler.java`에서 한국 시간으로 종목 다섯 시장과 공시 두 시장·접수일을 실행한다. 공시의 최근 접수일 재조회와 시장별 실패 재개를 각 실행기에 위임한다.
- [ ] T038 `src/test/java/batch/schedule/DailyCollectionIntegrationTest.java`에서 한 소스의 실패가 다른 소스와 다른 시장·접수일의 결과를 보존하는지 확인한다. 다음 절의 시험을 모두 실행해 신규·중복 건수, 공개 참조, 실패 실행과 재실행 결과를 대조한다.

## 8. 중복 적재와 재실행 판정 기준

| 작업 | 시험 입력과 실패 지점 | 합격 조건 |
| --- | --- | --- |
| T007, T016 | 시장별 정상본이 있는 상태에서 한 시장의 다운로드를 실패시킨 뒤 재실행한다. | 네 시장이 먼저 공개되고 재실행 시 다시 처리되지 않는다. 실패 시장은 기존 참조와 정상 시각을 유지하다가 복구 후에만 갱신된다. |
| T016 | KIS 파일을 고정하고 첫 청크 커밋 뒤 중단한다. 재시작 전 HTTP 응답 파일을 바꿔 둔다. | 같은 JobInstance에 별도 JobExecution이 기록되고 저장 원본 해시가 유지된다. 확정 청크 다음부터 이어가며 임시 행과 공개 종목의 키별 중복이 0건이다. 새 입력 리비전은 처음부터 처리된다. |
| T016, T018 | KIS 작업 프로세스를 종료해 실행 기록을 `STARTED`로 남긴다. | 기존 실행의 종료와 마지막 체크포인트를 확인한 뒤 복구할 수 있다. 재시작 결과가 같은 입력의 중단 없는 실행과 일치한다. |
| T017, T030 | KIS에 직전 정상본과 실제 변경이 있는 입력을 둔다. 쓰기를 마친 공개 커밋 직전과 커밋 직후 Step 완료 기록 전에 각각 중단한다. | 커밋 전에는 새 공개 스냅샷·이력·반영 표식이 없고 최신 참조와 정상 수집 시각은 직전 상태다. 커밋 후에는 재시작해도 이미 확정된 스냅샷과 이력을 유지한다. 두 경우 모두 재개 후 입력당 공개 스냅샷 1건, 최신 참조, 종목 집합, 변경 키별 전후 값과 표식이 중단 없는 실행과 일치한다. |
| T017 | 이전 KIS 실행이 실패한 동안 더 최신 정상본을 공개한 뒤 이전 실행을 재개한다. | 최신 정상본 참조·정상 수집 시각·이력 건수가 유지되고, 늦은 입력의 처리 결과는 운영 이력에서 확인된다. |
| T021 | 양 시장의 각 조회 묶음에 여러 페이지와 별도 접수번호의 정정 공시를 넣는다. 완료 후 같은 접수일을 새 리비전으로 재조회한다. | 실제 접수번호 집합이 시험본의 기대 집합과 같고 `COUNT(*) = COUNT(DISTINCT rcept_no)`다. 정정 제목이 보존되고 같은 응답의 신규 건수는 0건이다. 이후 늦은 공시 한 건을 추가하면 해당 날짜의 신규 건수가 1건이다. |
| T022 | 총 페이지 2, 총 건수 4인 응답에서 페이지 1은 `[A, B]`, 페이지 2는 `[B, C]`를 반환한다. 별도 시험에서 중복 없이도 고유 접수번호 수를 총 건수보다 적게 반환한다. | 두 경우 모두 시장·접수일·리비전이 미완료로 기록되고 새 공시는 0건이다. 같은 리비전에서 새 조회 차수의 첫 페이지부터 읽는다. 정상 응답 `[X, A]`, `[B, C]`로 복구하면 기대 접수번호 4건이 조회된다. |
| T022 | 한 시장은 첫 페이지 `013`을 반환한다. 다른 시장은 고유 접수번호 `Z`를 받은 뒤 중간 페이지에서 실패한다. 재개할 때는 `Z`가 없는 정상 묶음을 반환한다. | 첫 시장은 0건 성공이다. 실패 시장의 부분 적재는 0건이고 기존 공시와 종목 정상본이 보존된다. 같은 리비전으로 첫 페이지부터 다시 읽어 새 묶음의 기대 접수번호만 적재하고 이전 미완료 묶음의 `Z`는 공개하지 않는다. |
| T022 | 타임아웃 또는 일시적 서버 오류를 한 번 또는 설정 한도를 넘겨 주입한다. 별도 실행에서 `020`을 반환한다. | 일시 장애는 T002의 요청 횟수·지연을 따르고, 초과하면 미완료다. `020`은 같은 실행에서 반복 요청하지 않고 미완료로 남긴다. 운영 재개는 같은 조회 리비전을 사용한다. |
| T022 | 공시 전체 페이지 검증 후 공개 트랜잭션 커밋 전과 후에 각각 중단한다. | 커밋 전 실패는 공시·완료 표식·신규 건수를 확정하지 않는다. 재시작은 검증된 묶음을 재사용한다. 커밋 후 재시작은 공시를 더 쓰거나 기록된 신규 건수를 바꾸지 않는다. |
| T022, T027 | 자동 재조회 범위 밖의 날짜를 지정해 공시 조회 중과 공개 커밋 후 Step 완료 기록 전에 각각 작업 JVM을 강제 종료한다. | `STARTED` 실행의 이전 프로세스 종료를 확인한 뒤 같은 JobInstance와 리비전을 복구한다. 조회 중 종료는 첫 페이지부터 다시 읽고, 커밋 후 종료는 추가 조회나 적재 없이 완료 결과를 사용한다. 두 경우 모두 기대 공시 집합과 기록된 신규 건수가 중단 없는 실행과 일치한다. |

## 9. 실행 순서와 병행 작업

1. **공통 기반** — T001~T005로 실행 환경과 통합 시험 기반을 준비한다. T002에서 건수 감소·요청 재시도 기준과 시험 설정을 정한 뒤 두 수집의 관련 시험과 구현을 시작한다. T003 뒤에 T004와 T005를 병행할 수 있다.
2. **첫 검증 단위** — US-01에서 시험본으로 다섯 시장의 첫 정상본과 실패 분리를 확인한 뒤 US-02에서 입력·공개 복구를 먼저 검증한다. US-01의 파서 시험과 Job 시험, 다운로드와 파서 작업은 각각 병행할 수 있다. US-02의 입력 복구와 공개 복구는 서로 다른 파일에서 진행한다.
3. **공시 수집** — 순차 구현에서는 US-02 뒤에 US-04를 진행한다. 공통 기반이 준비되면 KIS 구현과 병행할 수 있으며 공시 적재에는 종목 마스터의 키가 필요하지 않다. US-04의 응답 시험과 재실행 시험은 병행할 수 있다. T023과 T024 뒤에는 페이지 검증과 공개 작업을 병행할 수 있다.
4. **변경 이력과 일일 실행** — US-03의 비교 로직과 이력 조회는 스키마가 준비된 뒤 병행한다. T020 뒤에 T035를 연동하고 T030을 통과하면 US-02의 변경 이력 복구를 포함한 인수 검증이 끝난다. T036에서 운영값을 확정한 뒤 T037·T038로 스케줄과 통합 검증을 진행한다. 다회차 KIS 운영 공개와 두 소스의 일일 실행은 T030·T038 통과 후 활성화한다.
