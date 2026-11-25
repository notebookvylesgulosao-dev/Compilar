# Quest Book — mod de missões para Minecraft 1.21.4 (Fabric, Java 21)

Inspirado no FTB Quests. Cliente e servidor (precisa nos dois lados) + Fabric API.

## Compilar
Requer **JDK 21**.

```
./gradlew build          # Windows: gradlew.bat build
```
O `.jar` fica em `build/libs/questbook-1.0.0.jar`. Para testar: `./gradlew runClient`.
Versões (em `gradle.properties`): MC 1.21.4, Yarn 1.21.4+build.8, Fabric API 0.119.4+1.21.4.

## Uso
- **J** abre/fecha o Quest Book (configurável em Opções > Controles > Quest Book).
- Arraste (botão esquerdo/direito) para mover, roda do mouse para zoom, **Home** centraliza.
- Busca no topo: filtra e destaca missões (Enter abre a primeira); clique no resultado para ir até ela.
- Clique numa missão: janela central de detalhes (fecha no **X** ou Esc). Entregas pelo botão *Entregar*, recompensas pelo botão *Resgatar* (ou *Resgatar tudo*).

## Objetivos
Coleta (inventário), Fabricação (bancada/fornalha/etc.), Entrega (consome itens), Combate, Exploração
(bioma, estrutura, dimensão, coordenadas+raio) e Interação (bloco/entidade). Alvos aceitam `#tags`.

## Recompensas
Item, pontos de XP, níveis de XP e comando (`%player%`; executado com permissão 4). Sempre resgate manual.

## Editor (administradores)
Quem tem permissão ≥ 2 (ou é o dono em mundo single-player) vê o botão **Editor**:
- Arraste missões para mover (grade de 0,5). Botão direito no vazio: nova missão; botão direito numa missão: editar.
- Shift+clique em A e depois em B: B passa a depender de A (repetir remove a ligação). Ciclos são recusados.
- Capítulos: `+ Capítulo` e `...` ao lado de cada capítulo (editar, subir, descer, excluir).
- Seletor de itens/blocos/entidades/biomas/estruturas/dimensões com busca (`@mod` filtra por mod), categorias e tags.

## Dados
`<mundo>/questbook/book.json` (definição das missões) e `<mundo>/questbook/progress/<uuid>.json` (progresso por jogador).
Mundo novo recebe um capítulo de exemplo. Salvamento atômico; progresso salvo a cada minuto, ao sair e ao parar o servidor.

## Comandos (permissão 2)
- `/questbook reload` — recarrega `book.json`
- `/questbook reset <jogadores> [missão]`
- `/questbook complete <jogadores> <missão>`

## Notas
- Progresso é individual e autoritativo no servidor; o cliente só recebe deltas.
- Limite de rede: cada edição enviada pelo cliente tem no máximo ~30 KB (descrição ≤ 1500 caracteres, ≤ 12 objetivos e ≤ 12 recompensas por missão).
