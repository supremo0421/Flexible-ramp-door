# RampDoor 0.2.1 테스트

## 0.2.1 좌표·다중 설치 회귀 검사

`coordinate-build.log`: Minecraft 26.2 GameTest 3개 통과 및 BUILD SUCCESSFUL. 기존 436블록/100회 왕복 v0.2 검사를 그대로 실행하며, `RampCoordinateTests`에서 다음을 추가 검증했습니다.

`coordinate-worldedit.log`: WorldEdit **7.4.5+7590-b8dc4c1**을 함께 로드한 동일 검사도 3개 모두 통과했습니다. 최종 JAR SHA-256: `fe132f135b5f7d2dc9719ad173940f53d94625ea654b02a1d0c7276332e6623e`.

- 좌표의 양끝을 포함한 폭·길이, 동서남북 네 방향, 음수 방향 힌지의 블록 경계, 뒤집힌 힌지 좌표 순서.
- 잘못된 대각선 힌지·높이·너무 큰 크기 거부.
- 실제 Brigadier 명령으로 상대좌표 생성, 절대좌표 생성, 두 모서리 캡처, 좌표 방향·경사 설정.
- 같은 이름으로 생성한 두 램프의 자동 `_2` 이름과 독립 템플릿, 중복 `create`의 `_3` 이름.
- 두 램프를 같은 청크들에 설치하고 동시에 열기. 각각의 Display 수와 통행면 유지.
- 하나를 cleanup/닫기/삭제해도 다른 열린 램프의 상태·충돌면 유지. 다른 램프의 위치가 잘못된 오래된 충돌 기록에 들어 있어도 정리 대상에서 제외.
- 설정 저장·재로드, 열린 상태의 좌표 설정 변경 거부.

아래 v0.2 크래시 로그와 수동 확인 항목은 이전 검증 기록이며, 0.2.1에서 강제 종료 시나리오를 다시 실행했다는 의미는 아닙니다.

이 문서는 자동 테스트가 **무엇을 검증했는지**, 그리고 서버 자동 검사로는 판정할 수 없어 **직접 확인해야 하는 항목**이 무엇인지 구분해 정리합니다.

## 검증 환경

| 항목 | 값 |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader / API | 0.19.5 / 0.159.0+26.2 |
| Loom / Gradle / JDK | 1.17.20 / 9.5.1 / Temurin 25.0.4.1+1 |
| 실행 환경 | Windows (프로젝트 전용 JDK, `./build-local.ps1`) |
| 대상 월드 | Gradle이 생성하는 일회용 GameTest 월드 |

테스트는 전용 서버 프로세스와 임시 테스트 월드에서만 실행됩니다. Minecraft 설치 폴더나 사용자의 기존 월드는 읽지도 수정하지도 않습니다. 테스트 소스(`src/gametest`)는 배포 JAR에 포함되지 않습니다.

## 실행 방법

```powershell
# 1. 빌드 + 기본 GameTest
./build-local.ps1                 # 또는 JAVA_HOME 설정 후 ./gradlew build

# 2. WorldEdit 동시 로드 호환성
./build-local.ps1 -WithWorldEdit  # 또는 ./gradlew runGameTest -PtestWorldEdit=<worldedit.jar> --rerun-tasks

# 3. 애니메이션 도중 강제 종료 복구 (JVM 2단계, 방향별로 각각 실행)
./gradlew.bat runGameTest -PcrashPhase=prepare_open  --rerun-tasks
./gradlew.bat runGameTest -PcrashPhase=verify_open   --rerun-tasks
./gradlew.bat runGameTest -PcrashPhase=prepare_close --rerun-tasks
./gradlew.bat runGameTest -PcrashPhase=verify_close  --rerun-tasks
```

`prepare_*` 단계는 애니메이션 중간에 `Runtime.halt(137)`로 테스트 JVM을 즉시 종료합니다. 따라서 **`BUILD FAILED`가 정상 결과**이며, 이어지는 `verify_*` 단계가 새 JVM에서 복구 결과를 판정합니다. `-PcrashPhase`는 테스트 소스 세트에만 적용되고 배포 JAR에는 영향을 주지 않습니다.

## 실행 결과 (2026-09-07)

| 로그 파일 | 실행 내용 | 결과 |
|---|---|---|
| `build.log` | 빌드 + 전체 GameTest | 통과, `rampdoor-0.2.0.jar` 생성 |
| `worldedit-test.log` | WorldEdit 7.4.5 동시 로드 후 동일 테스트 | 통과 |
| `crash-prepare-open.log` | OPENING 중 JVM 강제 종료 | 의도된 종료 (halt 137) |
| `crash-verify-open.log` | 새 JVM 재시작 후 복구 판정 | `PASS: fresh JVM restored CLOSED; 436 blocks; 0 displays; 0 collision blocks` |
| `crash-prepare-close.log` | CLOSING 중 JVM 강제 종료 | 의도된 종료 (halt 137) |
| `crash-verify-close.log` | 새 JVM 재시작 후 복구 판정 | `PASS: fresh JVM restored OPEN; 436 blocks; 436 displays; 480 collision blocks` |

CLOSING 중 강제 종료 복구가 **OPEN 상태의 Display 436개와 충돌 블록 480개를 그대로 되살린다**는 점이 v0.2에서 새로 검증된 항목입니다.

## v0.2 명세 테스트 대응

| # | 요구 사항 | 자동 검증 | 검증 방식 |
|---|---|---|---|
| 1 | 250~350+ 블록 화물 램프 캡처 | O | 436블록 예제를 캡처하고 블록 수·자동 축 판정 확인 |
| 2 | 열 때 램프 전체가 한 판처럼 32도 회전 | O | 모든 블록의 Display 변환이 단일 pivot·단일 각도 강체 회전 공식과 1e-3 이내로 일치하는지 대조 |
| 3 | Display 사이에 틈·어긋남 없음 | 부분 | 같은 대조가 위치 오차를 배제. 클라이언트 보간 구간의 시각적 확인은 수동 |
| 4 | 열린 상태에서 자연스러운 보행 | O | 실제 월드 충돌 shape을 1/8블록 간격으로 표본화해 구멍 없음, 표본 간 단차 ≤ 0.35블록, 보이는 경사면과의 편차 ≤ 0.2블록 확인 |
| 5 | collision이 외관을 가리지 않음 | O | 배치된 모든 충돌 블록의 렌더 shape이 INVISIBLE, occlusion shape이 비어 있음, 아이템 미등록 확인 |
| 6 | 닫으면 원래 블록·BlockState 정확 복원 | O | 템플릿 전체를 BlockState 단위로 대조 |
| 7 | 100회 왕복 후 손실·중복·고아 Display 없음 | O | 200회 이동(=100 왕복). 매 끝 상태마다 블록·Display 수·충돌 블록·청크 티켓 확인 |
| 8 | 열린 상태로 저장/재접속 유지 | O | 저장 파일을 별도 매니저로 재로딩해 상태·각도·축·템플릿 대조 후 복구, Display와 충돌면 재생성 확인 |
| 9 | 닫힌 상태 Display 0개 | O | 모든 CLOSED 검사에 포함 |
| 10 | 열린 상태에서 tick 애니메이션 없음 | O | OPEN 검사마다 `activeCount() == 0` 확인 |
| 11 | 램프 위 엔티티가 있으면 닫기 방지 | O | 램프 경로에 엔티티를 두고 close 거부, 상태 무변경 확인 |
| 12 | WorldEdit 동시 설치 | O | WorldEdit 동시 로드 상태로 전체 테스트 재실행 |
| 13 | OPENING 중단은 CLOSED, CLOSING 중단은 OPEN | O | 저장 파일 재로딩 복구 + 별도 JVM 강제 종료 시나리오 |

추가로 자동 검증하는 항목: BlockEntity/유체 캡처 거부, 이동 직전 구조 변경 감지, 목적지 장애물 거부, 블록 수 상한, 힌지 재설정 시 월드 위치 보존, 레드스톤 상승 에지 토글과 전원 유지 시 미작동, 열린 램프 삭제 거부, 애니메이션 중 외부 `setBlock` 차단.

## 수동으로 확인할 항목

자동 테스트는 서버 상태만 판정합니다. 다음은 클라이언트에서 직접 봐야 합니다.

- 열리는 동안의 **시각적 부드러움**과 ease-in/out 느낌. 서버는 keyframe만 보내고 구간은 클라이언트가 보간합니다.
- 램프가 하나의 금속판처럼 보이는지, 블록 사이가 벌어져 보이지 않는지.
- 열린 상태에서 실제로 걸어 올라가고 내려가지는지(점프 없이).
- 충돌면이 시야를 가리거나 파티클을 만들지 않는지.
- 큰 각도(60도 이상)나 매우 짧은 duration에서의 왜곡 정도.

## 알려진 한계

- 충돌면은 수평 1블록 격자 위의 근사입니다. 각도가 약 43도를 넘으면 `rise` 상한(15/16) 때문에 근사가 거칠어지고, 약 81도를 넘으면 충돌면을 만들지 않습니다.
- 애니메이션 도중에는 충돌이 없습니다. 램프는 이동 중 플레이어를 태우지 않습니다.
- 열린 램프의 청크가 언로드되면 Display는 청크와 함께 저장·복원됩니다. 다시 닫을 때는 캐시된 엔티티가 유효하지 않으면 같은 각도로 다시 만들어 회전시킵니다.
