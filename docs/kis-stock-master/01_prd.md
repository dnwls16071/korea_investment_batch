# 국내·미국 종목 마스터와 국내 주요 공시 수집 제품 요구사항

## 1. 사용자와 문제

대상 사용자는 국내·미국 종목 정보와 국내 주요 공시를 확인하는 개인이다. 종목 파일을 직접 내려받고 공시를 별도로 검색하면 시장별 최신 상태와 새 공시를 한 번에 확인하기 어렵다. 사용자는 검증된 종목 목록, 변경 이력, 새로 접수된 공시와 원문을 조회하고 싶다.

## 2. 목표와 성공 기준

- 사용자는 종목 파일을 매일 직접 내려받거나 합치는 작업 없이 국내·미국 종목 데이터를 확인할 수 있다.
- 시장별 마지막 정상 수집 시점과 그날의 실패 여부를 확인해 어느 시장의 데이터가 갱신됐는지 알 수 있다.
- 시장과 날짜를 기준으로 종목 변경 전후 값과 새로 적재된 주요 공시의 건수·제목·원문 링크를 확인할 수 있다.

## 3. 첫 출시 범위

### 포함

- **일일 동기화** — Spring Batch로 코스피·코스닥·나스닥·뉴욕·아멕스의 ZIP을 자동 수집하고, 원본과 수집 결과를 보관한다. 정상 처리 여부는 시장별로 확인할 수 있다.
- **검증 후 DB 반영** — 시장별 포맷에 따라 파싱하고 필수 항목, 중복 식별자, 비어 있거나 비정상적으로 감소한 레코드 수를 검사한다. 검증이 끝난 시장의 데이터만 그 시장의 최신 버전으로 반영한다.
- **적재 대상과 유형 코드 보존** — 대상 국내·미국 파일에 포함된 주식·ETF·ETN을 모두 적재한다. 조회용 유형과 함께 원본의 증권 유형 코드와 상품 구분 코드를 문자열로 보존한다.
- **변경 이력** — 같은 시장의 직전 정상 스냅샷과 비교해 신규 등장, 종목명 변경, 국내 거래정지 상태 변경, 목록에서 미확인, 재등장을 기록한다. 최초 수집은 비교 기준을 생성한다.
- **OpenDART 일일 수집** — 코스피·코스닥의 주요사항보고 전체를 접수일 기준으로 수집한다. 페이지가 여러 개면 끝까지 읽고 정정 공시도 함께 가져온다. 두 시장을 고정 수집 대상으로 둔다.
- **공시 적재 및 조회** — 공시를 접수번호 기준으로 한 건씩 저장한다. 접수번호·접수일·시장·종목코드·회사명·보고서 제목을 보존하고, 접수번호에서 연결한 DART 원문 링크를 제공한다. 수집 결과에서 신규 공시 건수와 시장별 오류를 확인할 수 있다.

### 제외

- 미국 외 해외 시장, 코넥스, 별도 지수·채권·선물·옵션 마스터 수집
- 실시간 시세, 주문 실행, 잔고 연동, 투자 추천, 과거 마스터의 소급 구축
- 관심 종목 관리, 개인화 필터와 알림 발송
- 주요사항보고 외 공시, 공시 본문·첨부파일 다운로드, 기업개황과 재무제표 수집

## 4. 공식 입력 파일과 제공 필드

### 한국투자증권 종목 마스터

한국투자증권은 종목정보 마스터를 매일 여러 시각에 갱신한다고 안내한다. 공식 개요에 게시된 마지막 갱신 시각은 `18:55`이며, 시장별 실제 반영 시점과 배치 시각은 도입 시 확인한다. [KIS API 개요의 종목정보 파일](https://apiportal.koreainvestment.com/apiservice) · [KIS 종목정보파일 메뉴](https://apiportal.koreainvestment.com/apiservice-category)

| 국가 | 시장 | 다운로드 ZIP | 압축 안의 파일 | 공식 자료 |
| --- | --- | --- | --- | --- |
| 한국 | 코스피 | [`kospi_code.mst.zip`](https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip) | `kospi_code.mst` | [KIS 코스피 정제 예제](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/kis_kospi_code_mst.py) |
| 한국 | 코스닥 | [`kosdaq_code.mst.zip`](https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip) | `kosdaq_code.mst` | [KIS 코스닥 정제 예제](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/kis_kosdaq_code_mst.py) |
| 미국 | 나스닥 | [`nasmst.cod.zip`](https://new.real.download.dws.co.kr/common/master/nasmst.cod.zip) | `nasmst.cod` | [KIS 해외주식 정제 예제의 `nas`](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/overseas_stock_code.py) |
| 미국 | 뉴욕 | [`nysmst.cod.zip`](https://new.real.download.dws.co.kr/common/master/nysmst.cod.zip) | `nysmst.cod` | [KIS 해외주식 정제 예제의 `nys`](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/overseas_stock_code.py) |
| 미국 | 아멕스 | [`amsmst.cod.zip`](https://new.real.download.dws.co.kr/common/master/amsmst.cod.zip) | `amsmst.cod` | [KIS 해외주식 정제 예제의 `ams`](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/overseas_stock_code.py) |

- **국내** — 공식 예제는 CP949로 파일을 읽고 단축코드·표준코드·한글 종목명을 분리한 뒤 시장별 고정 폭 레이아웃으로 나머지 필드를 읽는다. 증권그룹, 업종, 상장일, 거래정지 여부 등이 있고 코스피와 코스닥의 후반부 레이아웃은 다르다. [KIS 코스피 정제 예제](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/kis_kospi_code_mst.py) · [KIS 코스닥 정제 예제](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/kis_kosdaq_code_mst.py)
- **미국** — 공식 해외주식 정제 예제는 같은 CP949·탭 구분 파싱을 세 시장에 쓰며 24개 열 이름을 정의한다. 주요 필드는 국가·거래소 코드, 일반·실시간 심볼, 한글명·영문명, 증권유형, 통화, 업종, 호가단위 유형이다. 예제 열 정의에는 거래정지 여부가 없고, 실제 파일의 헤더 유무와 열 수는 구현 전 샘플로 확인한다. [KIS 해외주식 정제 예제와 열 정의](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/overseas_stock_code.py)
- **원본 유형 코드** — 국내 파일은 증권그룹 구분 코드와 ETP 상품 구분 코드를 제공한다. 미국 파일은 증권유형과 상품 구분 코드를 제공하며, KIS 공식 예제는 미국 상품 구분 코드 `001`을 ETF, `002`를 ETN으로 정의한다. [KIS 코스피 마스터 규격](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/%EC%A2%85%EB%AA%A9%EB%A7%88%EC%8A%A4%ED%84%B0%EC%A0%95%EB%B3%B4%28%EC%BD%94%EC%8A%A4%ED%94%BC%29.h) · [KIS 코스닥 정제 예제](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/kis_kosdaq_code_mst.py) · [KIS 해외주식 정제 예제](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/overseas_stock_code.py)
- **자동 다운로드** — 공식 예제는 포털 화면과 별도로 다운로드 주소에 직접 요청해 ZIP을 가져온다. 첫 출시는 이 주소를 설정으로 관리해 매일 같은 흐름으로 실행한다. [KIS 국내 다운로드 예제](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/kis_kospi_code_mst.py) · [KIS 해외 다운로드 예제](https://github.com/koreainvestment/open-trading-api/blob/main/stocks_info/overseas_stock_code.py)

### OpenDART 주요사항보고

- **공시검색 요청** — `list.json`에 접수일 범위(`bgn_de`, `end_de`), 공시유형 `B`, 법인구분 `Y`(유가) 또는 `K`(코스닥)를 보낸다. 법인구분은 한 요청에 하나씩 지정하며, `last_reprt_at=N`은 정정 공시를 포함한다. [OpenDART 공시검색 요청·유형](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019001)
- **공시검색 응답** — 접수번호는 `rcept_no`, 접수일은 `rcept_dt`, 시장은 `corp_cls`, 종목코드는 `stock_code`, 회사명은 `corp_name`, 보고서 제목은 `report_nm`이다. 14자리 접수번호를 DART 공시뷰어의 `rcpNo`에 넣어 원문을 열 수 있다. [OpenDART 응답과 공시뷰어 규격](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019001)

## 5. 사용자 흐름과 제안 정책

1. 예정된 실행에 한국투자증권 파일 다섯 개를 수집한다. 시장별로 원본 식별 정보, 수집 시각, 건수, 검증 결과를 남긴다. 원천 효력일을 확인할 수 있으면 수집 시각과 구분한다.
2. 검증된 종목 스냅샷과 변경 이력을 시장 단위로 반영한다. 종목 코드와 표준코드는 문자열로 보관한다. 실패 시장은 마지막 정상본을 유지하고, 정상인 시장은 각자의 결과를 갱신한다.
3. 별도로 OpenDART에서 코스피·코스닥 공시를 접수일 기준으로 가져온다. 각 시장의 전체 페이지를 확인한 뒤 접수번호를 기준으로 반영한다. 조회 결과가 빈 회차와 요청·검증 실패를 구분해 표시한다.
4. 사용자는 종목 변경 전후 값과 새 공시의 제목·원문 링크를 조회한다. 정상본에서 사라진 종목은 `목록에서 미확인`으로 표시한다.
5. 실패한 실행을 다시 시작하면 종목 마스터는 이미 성공한 시장을 식별하고, 공시는 실패한 시장·접수일을 다시 조회한다. 재실행해도 종목 변경 이력과 공시 접수번호는 중복되지 않는다.

## 6. 인수 조건

| 시나리오 | 사전 조건 | 사용자 행동 또는 사건 | 관찰 가능한 결과 |
| --- | --- | --- | --- |
| 최초 종목 수집 | 다섯 시장의 정상 ZIP이 준비됐다 | 일일 배치가 시작된다 | 다섯 시장이 DB에서 조회되고 시장별 수집 결과가 기록된다. 최초 정상본은 비교 기준이 된다. |
| 국내·미국 포맷 검증 | 국내와 미국의 정상 샘플에 한글, 국내 선행 0, 미국 심볼이 포함됐다 | 파일을 검증하고 적재한다 | 코드와 종목명이 원문과 일치하고 시장·유형 필드가 구분된다. 같은 심볼이 다른 시장에 있어도 별도로 조회된다. |
| 증권유형 적재 | 국내와 미국의 테스트 샘플에 주식·ETF·ETN이 포함됐다 | 파일을 검증하고 적재한다 | 파일에 있는 세 유형이 모두 조회된다. 조회용 유형과 원본 유형 코드가 함께 남고 `001`, `002` 같은 코드의 선행 0도 보존된다. |
| 종목 변경 비교 | 직전 정상본이 있다 | 새 파일에서 여러 유형의 종목 이름이 바뀐다 | 시장·유형·코드와 변경 전후 값을 이력에서 확인할 수 있다. |
| 미확인과 재등장 | 이전 정상본에 종목이 있다 | 검증을 통과한 목록에서 종목이 빠졌다가 다음 정상본에 다시 나타난다 | `목록에서 미확인`과 `재등장`이 순서대로 기록된다. |
| 변경 없음 | 직전 정상본과 종목 데이터가 같다 | 새 회차가 파일을 정상 수집한다 | 수집 성공 기록만 갱신되며 변경 이력은 추가되지 않는다. |
| 시장별 실패 | 다섯 시장의 마지막 정상 데이터가 있다 | 한 시장의 다운로드가 실패하거나 검증 결과가 기준에 미달한다 | 해당 시장은 기존 정상본과 시각을 유지하고 오류 이유를 표시한다. 정상인 다른 시장은 갱신된다. 실패한 시장에는 신규·미확인 변경이 생기지 않는다. |
| 종목 배치 재실행 | 일부 시장의 반영이 끝난 회차가 있다 | 같은 회차를 같은 입력으로 재실행한다 | 남은 처리를 재개할 수 있고 종목 데이터와 변경 이력이 중복되지 않는다. |
| 공시 전체 수집 | 양 시장의 해당 접수일에 여러 페이지와 정정 공시가 있다 | 공시 배치를 실행한다 | 모든 페이지의 대상 공시가 조회되고, 정정 공시의 원문 제목과 자체 접수번호가 보존된다. |
| 공시 중복 제거 | 이미 적재한 접수번호가 있다 | 같은 접수일을 재처리한다 | 접수번호당 한 행만 남고 새로 적재된 공시 건수에 중복이 포함되지 않는다. |
| 공시 빈 결과와 장애 | 한 시장은 결과가 없고 다른 시장은 페이지 조회가 실패한다 | 공시 배치를 실행한다 | 빈 시장은 0건 성공으로, 실패 시장은 원인과 함께 실패로 기록한다. 실패 시장의 해당 날짜는 재실행할 수 있고 기존 공시와 종목 마스터는 조회할 수 있다. |

## 7. 열린 질문과 근거

- 실제 파일을 며칠 관찰해 국내·미국 시장에 공통 일일 실행 시각을 둘지, 시장 그룹마다 나눌지 정한다. 휴장일에 같은 데이터가 다시 제공될 때도 마지막 정상 데이터를 조회할 수 있게 한다.
- OpenDART 실행 시간, 누락 방지를 위해 최근 접수일을 다시 조회할 범위, 페이지 실패 시 재시도 횟수를 정한다.
- 시장별 레코드 감소를 차단하는 기준과 원본 ZIP 보존 기간을 정한다.
- 공식 확인 범위는 KIS 포털의 종목정보파일 안내와 KIS 공식 저장소의 다운로드·파싱 예제다. 실파일의 첫 행, 헤더 유무, 최신 열 수는 구현 전 샘플로 확인한다. [KIS 종목정보파일](https://apiportal.koreainvestment.com/apiservice-category) · [KIS 종목정보 공식 예제 목록](https://github.com/koreainvestment/open-trading-api/tree/main/stocks_info)
