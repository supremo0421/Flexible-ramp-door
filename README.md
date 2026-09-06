# RampDoor 0.2.1

[English README](README.en.md) · 한국어 문서

## 0.2.1 추가 기능

크기·방향을 좌표로 지정하고 같은 이름으로 여러 램프를 생성할 수 있습니다. [좌표 사용법](COORDINATES.md)에 복사해 쓸 수 있는 예시를 정리했습니다.

- `/ramp demo <id> <힌지시작 x y z> <힌지끝 x y z> <앞끝 x y z>`: 좌표로 폭·길이·수평 방향을 정해 새 램프 생성.
- `/ramp capture <id> <x1 y1 z1> <x2 y2 z2>`: 기존 구조물을 두 모서리로 직접 캡처.
- `/ramp direction <id> <x y z>`: 힌지에서 목표 좌표를 향하는 방향·경사 지정.
- `create`/`demo`의 이름이 이미 있으면 `_2`, `_3`을 자동으로 붙입니다. `/ramp demo`는 이름도 자동 생성합니다.
- 다른 램프의 블록·충돌면을 덮거나 삭제하지 않도록 다중 램프 소유권 검사를 추가했습니다.

Minecraft Java Edition **26.2 / Fabric**에서 사용자가 만든 블록 구조물을 SF 화물 램프처럼 움직이는 모드입니다. 닫힌 상태는 실제 블록이고, 열린 상태는 힌지를 기준으로 회전한 BlockDisplay와 보이지 않는 충돌면입니다. WorldEdit, Create, Valkyrien Skies는 필수 의존성이 아닙니다.

## v0.1에서 바뀐 점

v0.1은 CLOSED_TEMPLATE과 OPEN_TEMPLATE 두 구조를 따로 캡처했습니다. 실제 블록으로 32도 경사를 만들면 거대한 계단이 되어 원래 램프 디자인이 무너졌습니다. **v0.2는 OPEN_TEMPLATE 방식을 폐기합니다.**

```
CLOSED   실제 Minecraft 블록
   ↓ OPENING   BlockDisplay 강체 회전
OPEN     회전한 BlockDisplay 유지 + 보이지 않는 walkable collision
   ↓ CLOSING   collision 제거 후 역회전
CLOSED   Display 제거, 원래 실제 블록 복원
```

- 캡처는 하나뿐입니다. `/ramp capture <id>` — 닫힌 상태의 실제 구조물만 저장합니다.
- 열린 모습은 저장된 구조가 아니라 이 템플릿을 힌지 기준으로 회전시킨 **수학적 결과**입니다.
- 열린 상태에서도 Display가 남습니다. 이는 "정지 상태 Display 0개" 정책의 의도된 예외입니다. 닫힌 상태에서는 여전히 0개입니다.
- 열린 상태의 통행은 `rampdoor:ramp_collision`이라는 보이지 않는 블록이 담당합니다.
- v0.1 저장 파일은 자동 이관됩니다. `closed` 템플릿만 남기고 `open`은 버리며, 각 램프를 복구 저널에 넣습니다. 이관 후 `/ramp cleanup <id>`를 한 번 실행하면 실제 닫힘 블록이 제자리에 놓입니다.

## 설치와 빌드

- Minecraft **26.2**, Java **25**, Fabric Loader **0.19.3 이상**, 해당 Minecraft 버전의 Fabric API가 필요합니다.
- 개발 검증 버전: Loader **0.19.5**, Fabric API **0.159.0+26.2**, Loom **1.17.20**, Gradle **9.5.1**.
- `build/libs/rampdoor-0.2.1.jar`를 서버와 접속 클라이언트의 `mods` 폴더에 넣습니다. 기존 RampDoor JAR는 빼서 **RampDoor JAR 하나만** 남깁니다. `-sources.jar`는 설치용 파일이 아닙니다.
- 기존 월드에서 처음 사용할 때는 복사본으로 시험하는 것을 권장합니다.

Java 25 JDK를 `JAVA_HOME`에 설정한 뒤:

```sh
./gradlew build
```

Windows PowerShell에서는 `./gradlew.bat build`, 이 PC에서는 프로젝트 전용 JDK를 쓰는 `./build-local.ps1`을 사용합니다.

## 빠른 테스트

OP 또는 싱글플레이 치트가 필요합니다. 폭 16 × 길이 22 × 높이 2의 빈 공간이 필요하고, 램프가 내려갈 앞쪽 아래로 약 14블록의 여유가 있어야 합니다.

```mcfunction
/ramp demo cargo_ramp
/ramp open cargo_ramp
/ramp close cargo_ramp
/give @s rampdoor:ramp_controller
/ramp bind cargo_ramp
```

예제는 **436블록**짜리 화물 램프입니다. 중앙 armored panel(white concrete + smooth quartz 가로 리브), 좌우 reinforced edge(polished deepslate), 2줄 traction strip(iron block), light gray 보조 패널, 두꺼운 front lip(quartz block), 2개의 hinge housing으로 구성하며 가장자리·앞단·힌지부는 2블록 두께입니다. 힌지는 데크 윗면에 놓여 통행면이 힌지를 정확히 축으로 회전합니다.

첨부된 화물 출입구 이미지는 분위기 참고입니다. GLB를 복셀화하지 않습니다.

## 자신의 구조물 등록

닫힌 상태의 램프만 만들면 됩니다.

```mcfunction
/ramp create cargo_ramp
/ramp pos1
/ramp pos2
/ramp capture cargo_ramp
/ramp hinge cargo_ramp 124 72 -91
/ramp angle cargo_ramp 32
/ramp duration cargo_ramp 40
/ramp open cargo_ramp
```

`pos1`/`pos2`는 64블록 이내 바라보는 블록, 맞은 블록이 없으면 발 위치를 사용합니다. 캡처 시 힌지 축과 램프가 뻗는 방향을 구조물에서 자동으로 판정합니다. 자동 판정이 틀리면 `/ramp axis`, `/ramp extend`, `/ramp direction`으로 직접 지정합니다.

## 각도와 좌표

`angle`은 **닫힌 상태에서 열린 상태까지 내려가는 각도**입니다. 열린 뒤 지면과 이루는 경사각과 같습니다. 기본 32도, 허용 범위 10~90도입니다.

- 힌지는 데크 **윗면**(통행면)에 두는 것이 가장 자연스럽습니다. `/ramp hinge cargo_ramp 124 73 -91`
- `axis x`: X축이 힌지, 램프는 ±Z로 뻗습니다. `axis z`: Z축이 힌지, 램프는 ±X로 뻗습니다.
- `extend positive|negative`: 힌지에서 램프가 뻗는 부호. `direction down|up`: 열릴 때 내려갈지 올라갈지.
- 소수점 힌지를 지원합니다. 블록은 정수 원점 기준 상대좌표로, 힌지는 소수점 정밀도로 저장합니다. 힌지를 바꾸면 실제 월드 위치를 보존하며 상대좌표를 다시 계산합니다.
- 약 43도까지는 통행면이 정확합니다. 그보다 가파르면 `rise` 상한 때문에 근사가 거칠어지고, 81도를 넘으면 충돌면을 만들지 않습니다(시각적 회전은 정상). `/ramp debug`의 `walkable` 항목으로 확인합니다.

## 강체 회전과 애니메이션

각 블록 좌표 `P`는 힌지 `H`에 대해 `P' = H + R(P - H)`로 이동하고, 블록 자체의 방향도 같은 `R`로 회전합니다. 모든 Display가 같은 pivot, 같은 각도, 같은 보간 구간을 공유하므로 램프는 한 장의 금속판처럼 움직입니다.

애니메이션은 서버가 매 tick 변환을 다시 보내지 않습니다. duration을 4~16개의 keyframe으로 나눠 각 구간의 목표 변환과 보간 길이만 전송하고, 구간 내부는 클라이언트가 보간합니다. 기본 곡선은 `ease_in_out`(smoothstep)이고 `/ramp easing <id> linear`로 바꿀 수 있습니다. 기본 duration은 40 ticks(약 2초)입니다.

## 보이지 않는 충돌면

BlockDisplay에는 충돌이 없으므로 열린 상태에서 `rampdoor:ramp_collision`을 자동 배치합니다.

- 렌더링·광원 차단·타겟팅 없음, 아이템 없음, 크리에이티브 인벤토리 없음, 드롭 없음, 일반 설치 불가. RampDoor 내부만 생성·제거합니다.
- 상태: `height=1..31`(오르막 쪽 모서리의 1/16 단위 높이), `rise=0..15`(블록을 가로지르며 내려가는 양), `facing`(오르막 방향). 16을 넘는 height는 통행면이 이 블록의 윗면을 뚫고 나간다는 뜻이고, 블록 아래로 내려간 sub-step은 아예 생성하지 않습니다. 그래서 블록 경계를 가로지르는 경사도 위·아래 두 블록이 같은 직선을 표현합니다.
- 실제 shape은 경사 방향으로 8등분한 **micro-step**입니다. 32도 램프의 블록당 단차는 0.625블록으로 바닐라 자동 계단 높이 0.6을 넘기 때문에, 한 블록을 8단으로 나눠 한 단을 약 0.08블록으로 만듭니다. 그래서 점프 없이 걸어 올라갑니다.
- 배치는 캡처된 각 기둥의 윗면을 힌지 기준으로 회전시켜 계산합니다. 즉 보이는 면과 밟는 면이 같은 수식에서 나옵니다. 32도 예제에서 실측 편차는 0.09블록 이하, 1/8블록 간격 표본 사이의 최대 단차는 0.15블록입니다.
- `/ramp collision <id> auto`가 기본입니다. 장식용 측면까지 통행면으로 만들 필요가 없으면 `/ramp collision <id> width 14`, `/ramp collision <id> length 20`으로 중앙 영역만 지정합니다.

## 명령어

모든 `/ramp` 명령은 OP level 2 이상입니다. 연결된 컨트롤러의 일반 우클릭은 일반 플레이어도 가능합니다. 관전자는 작동시키지 않습니다.

| 명령 | 기능 |
|---|---|
| `create <id>` | 현재 차원에 새 정의 생성. ID는 영문 소문자·숫자·`_`·`-`, 1~64자 |
| `delete <id>` | 등록 삭제. 닫힌 상태에서만 가능하며 실제 블록은 건축물로 남김 |
| `list` / `info <id>` | 목록 / 요약 |
| `debug <id>` | 상태, 블록 수, Display 수, collision 수, 힌지, 축, 각도, duration, 컨트롤러, 청크 범위 |
| `pos1 [x y z]` / `pos2 [x y z]` | 선택 영역 지정 |
| `capture <id>` | 닫힌 실제 구조물을 RAMP_TEMPLATE으로 캡처 |
| `hinge <id> [x y z]` | 힌지 설정. 좌표 생략 시 바라보는 블록 |
| `axis <id> x\|z` | 힌지 축 고정(자동 판정 해제) |
| `extend <id> positive\|negative` | 램프가 뻗는 방향 고정 |
| `direction <id> down\|up` | 열림 방향 |
| `angle <id> <10..90>` | 열림 각도 |
| `duration <id> <2..1200>` | 기본 40 ticks |
| `easing <id> ease_in_out\|linear` | 보간 곡선 |
| `collision <id> auto\|width <n>\|length <n>` | 통행면 범위 |
| `bind <id>` | 다음 컨트롤러 우클릭으로 연결. 램프당 컨트롤러 하나 |
| `open <id>` / `close <id>` / `toggle <id>` | 동작 요청. 움직이는 중의 요청은 무시 |
| `cleanup <id>` (별칭 `recover`) | 고아 Display·collision을 지우고 현재 안전 끝 상태를 복구 |
| `demo <id>` | 빈 공간에 436블록 예제 생성 |

컨트롤러 제작법은 철 주괴 8개로 레드스톤 가루 1개를 둘러싸는 조합입니다. 지속 전원은 반복 작동시키지 않고 OFF→ON 전환만 감지합니다.

## 안전 장치

- 닫을 때 램프가 지나가는 부피(회전 구간을 표본화한 합집합) 안에 플레이어나 몹이 있으면 닫지 않고 `Ramp cannot close: entity obstructing movement.`를 알립니다. v0.2는 대기하지 않고 취소합니다.
- 열 때는 충돌면이 놓일 자리가 비어 있는지 검사합니다. 막혀 있으면 움직이지 않습니다.
- BlockEntity, moving piston, 유체, waterlogged 블록, 그리고 `ramp_collision` 자신은 캡처를 거부합니다.
- 이동 직전에 실제 블록이 템플릿과 일치하는지 확인합니다. 고쳐 지었다면 다시 캡처해야 합니다.
- 다른 램프의 템플릿과 겹치는 캡처를 거부합니다.

## 저장과 복구

- 저장 파일은 `<world>/data/rampdoor.json`(format 2)입니다. id, 차원, 힌지, 축·extend·direction, 각도, duration, easing, 템플릿, 충돌면 좌표, 컨트롤러, 상태를 담습니다. 임시 파일 `force(true)` 후 atomic replace로 저장하며, 손상 시 빈 데이터로 덮어쓰지 않습니다.
- 실제 블록을 지우기 전에 OPENING/CLOSING과 복구 저널을 먼저 디스크에 기록합니다. 끝난 뒤에는 변경 청크를 flush한 다음 저널을 지웁니다.
- **열린 상태로 저장·재접속할 수 있습니다.** 서버 시작 시 OPEN 램프는 Display를 새 세대 태그로 다시 만들고 충돌면을 복원합니다. 예전 세대의 Display는 청크가 늦게 로드돼도 엔티티 로드 시점에 정리됩니다.
- OPENING 중 종료는 CLOSED로, CLOSING 중 종료는 OPEN으로 복구합니다.
- Display에는 `rampdoor:<ramp_id>` 태그가 붙어 고아 Display를 램프 단위로 정리할 수 있습니다. 충돌면 좌표는 램프 정의에 기록되어 `cleanup`이 정확히 제거합니다.
- 복구 위치에 템플릿에 없는 다른 블록이 있으면 덮어쓰지 않고 중단합니다. 로그의 좌표에서 장애물을 치운 뒤 `/ramp cleanup`을 실행합니다.
- 움직이는 동안 원본·충돌면 위치의 일반 `Level.setBlock`을 막습니다. 청크를 직접 수정하는 외부 도구는 이 보호를 우회하므로 움직이는 영역을 WorldEdit으로 편집하지 않습니다.

## 성능

닫힌 램프는 Display 0개, ticker 0개, 반복 작업 0개입니다. 열린 램프는 정적인 Display와 정적인 충돌 블록만 존재하며 tick 계산을 하지 않습니다. 변환 계산은 애니메이션 중의 keyframe 시점에만 수행합니다. 기본 상한은 램프당 블록 512개, 충돌 블록 2048개, 동시 애니메이션 4개입니다.

## 설정

처음 실행하면 `config/rampdoor.json`이 생성됩니다. 변경 후 서버를 재시작합니다.

```json
{
  "max_blocks_per_ramp": 512,
  "max_collision_blocks_per_ramp": 2048,
  "max_simultaneous_animations": 4,
  "default_duration_ticks": 40,
  "default_open_angle": 32.0,
  "max_selection_volume": 262144
}
```

허용 범위는 각각 1~16384, 1~65536, 1~64, 2~1200, 10~90, 1~16777216입니다. 잘못된 설정은 조용히 무시하지 않고 오류로 알립니다.

## 한계

- Minecraft에는 임의 각도의 실제 블록이 없습니다. 열린 램프의 겉모습은 Display이므로 블록 파괴·상호작용·조명 갱신의 대상이 아니며, 통행은 보이지 않는 충돌면이 담당합니다.
- 충돌면은 수평 1블록 단위 격자 위의 근사입니다. 수직으로는 1/16 단위 micro-step이라 걷기에는 충분하지만 완전한 연속 경사면은 아닙니다.
- 움직이는 도중에는 충돌이 없습니다. 램프는 애니메이션 중 플레이어·몹·차량을 실어 나르지 않습니다.
- 레드스톤 기계, 유체, 중력 장치를 옮기는 범용 contraption 시스템으로 확장하지 않습니다. RampDoor는 램프 기능에만 집중합니다.

## 테스트

`./gradlew build` 또는 `./gradlew runGameTest`로 별도 GameTest 월드를 사용합니다. `src/gametest`의 테스트는 배포 JAR에 포함되지 않습니다. 상세 결과와 수동 확인 항목은 [TESTING.md](TESTING.md)를 확인하세요.

WorldEdit을 선택적으로 함께 로드하는 테스트:

```sh
./gradlew runGameTest -PtestWorldEdit=/absolute/path/to/worldedit.jar --rerun-tasks
```

WorldEdit API를 호출하거나 배포 JAR에 번들하지 않습니다.
