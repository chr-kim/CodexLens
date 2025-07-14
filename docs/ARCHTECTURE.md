# Codex Lens 아키텍처/기술 명세서

## 1. 시스템 개요

- **목적:**
  스마트폰 카메라로 외국어 원서를 실시간 인식·번역하고, 중요한 구절과 사용자의 생각을 독서 노트로 기록하는 AI 기반 모바일 독서 보조 앱
- **주요 기능:**
  실시간 카메라 프리뷰, 텍스트 인식(OCR), 터치 기반 번역, 독서 노트 저장/목록, 메모/태그, 갤러리 이미지 번역
- **타겟 사용자:**
  해외 원서로 공부하는 대학생, 대학원생, 엔지니어 지망생 등


## 2. 전체 아키텍처 개요

- **아키텍처 패턴:**
  MVVM(Model-View-ViewModel) + Repository 패턴
- **계층 구조 및 역할:**
  - **Presentation(UI) Layer:** Jetpack Compose 기반 화면, 사용자 인터랙션 처리
  - **Domain Layer:** ViewModel, UseCase 등 비즈니스 로직 담당
  - **Data Layer:** Repository, Room DB, ML Kit 연동 등 데이터 관리
- **주요 컴포넌트 다이어그램(텍스트 요약):**
  - MainScreen ↔ MainViewModel ↔ NoteRepository ↔ Room DB
  - MainScreen → CameraX, ML Kit OCR/번역
  - MainScreen → 갤러리 이미지 선택 → ML Kit OCR/번역


## 3. 기술 스택 및 버전

| 분류 | 기술/라이브러리 | 버전(예시) | 비고 |
| :-- | :-- | :-- | :-- |
| 언어 | Kotlin | 1.9.x |  |
| UI | Jetpack Compose | 1.6.x |  |
| 카메라 | Android CameraX | 1.5.0-beta01 |  |
| OCR | Google ML Kit Text Recognition | 16.0.0 |  |
| 번역 | Google ML Kit On-device Translation | 17.0.1 |  |
| DB | Room Persistence Library | 2.6.x |  |
| 빌드 | Gradle (Kotlin DSL) | 8.x |  |
| 버전관리 | Git, Gitea |  |  |

## 4. 주요 컴포넌트/모듈 상세

- **ReadingNote (Entity):**
  id, originalText, translatedText, timestamp, memo, tags
- **ReadingNoteDao:**
  insert, delete, getAll 등 CRUD 메서드
- **AppDatabase:**
  Room DB 추상 클래스, Dao 제공
- **MainViewModel:**
  UI 상태 관리, 노트 추가/삭제/조회 로직
- **CameraManager:**
  CameraX 프리뷰, 이미지 분석
- **MLKitManager:**
  OCR, 번역 기능 래핑
- **NoteRepository:**
  데이터 접근 추상화, ViewModel과 DB/ML Kit 연결


## 5. 데이터 구조/ERD

| 엔터티명 | 필드명 | 타입 | 설명 | 제약조건 |
| :-- | :-- | :-- | :-- | :-- |
| ReadingNote | id | Long | 고유 식별자 | PK, AutoInc |
|  | originalText | String | 원문 | Not Null |
|  | translatedText | String | 번역문 | Not Null |
|  | timestamp | Long | 저장 시각 | Not Null |
|  | memo | String | 개인 메모 | Nullable |
|  | tags | String | 태그(쉼표 구분) | Nullable |

## 6. API/외부 연동 명세 (필요 시)

- **ML Kit Text Recognition API:**
  입력: 이미지 프레임
  출력: 인식된 텍스트, 위치 좌표
- **ML Kit Translation API:**
  입력: 원문 텍스트
  출력: 번역문
  예외: 네트워크 오류, 번역 실패 등


## 7. 비기능 요구사항

- **성능:**
  실시간 프리뷰 및 텍스트 인식 1초 이내 응답
- **보안:**
  민감 정보 저장/전송 없음, 권한 최소화
- **확장성:**
  User, Tag, 클라우드 연동 등 용이
- **유지보수성:**
  KDoc 주석, 모듈화, 테스트 코드 작성


## 8. 설계/기술적 의사결정 근거

- **Jetpack Compose:**
  최신 UI, 생산성·유지보수성 우수
- **CameraX/ML Kit:**
  구글 공식, 성능·호환성·문서화 강점
- **Room:**
  ORM 기반, 타입 안정성, 마이그레이션 용이
- **MVVM:**
  UI-로직 분리, 테스트·확장성 강화


## 9. 빌드/배포/운영 환경

- **빌드:** Android Studio, Gradle
- **배포:** APK, Gitea 저장소
- **운영:** 로컬 테스트, 추후 Firebase Crashlytics 등 연동 가능


## 10. 참고 및 관리

- **/docs/ARCHITECTURE.md**로 별도 관리, 변경 이력 기록
- **PlantUML 등 다이어그램 도구**로 구조 시각화
- **기능 명세서, 사용자 시나리오, ERD와 연동**해 일관성 유지


