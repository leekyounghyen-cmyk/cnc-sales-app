# 씨앤씨 영업 현황 (휴대폰 앱)

앱처럼 설치되는 웹앱(PWA) 시작 페이지예요. 실제 화면과 데이터는 Apps Script 웹 앱에서 불러와요.

- `index.html` : 시작 페이지 (맨 아래 APP_URL = Apps Script 배포 주소)
- `manifest.webmanifest` : 앱 이름·아이콘·전체 화면 설정
- `sw.js` : 오프라인용 기본 파일 저장
- `icons/` : 앱 아이콘

## 올리는 법 (둘 중 하나)
1. GitHub Pages: 새 저장소에 이 폴더 내용을 올리고 Settings → Pages → Branch: main / root → 저장
2. Netlify Drop: https://app.netlify.com/drop 에 이 폴더를 끌어다 놓기 (무료 계정 필요)

## 휴대폰에 설치
- 안드로이드(크롬): 주소 열기 → 아래 「설치」 버튼 또는 메뉴(⋮) → 「앱 설치 / 홈 화면에 추가」
- 아이폰(사파리): 주소 열기 → 공유(□↑) → 「홈 화면에 추가」
