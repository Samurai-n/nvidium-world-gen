# Investigação e decisão experimental — 26.2

Estado: protótipo 26.2 compilado e testado em cliente real. Leitura de código e testes pequenos não comprovam desempenho em grande escala.

## O que já existe

**Bobby 5.2.15 / Minecraft 26.2** já resolve parte substancial do produto: grava chunks recebidas, recupera após reconexão, disponibiliza chunks falsas quando a real não existe e notifica o Sodium. `FakeChunkManager.load`, `ChunkSerializer.deserialize`, `ClientChunkCacheMixin.bobbyGetChunk` e `SodiumClientChunkCacheMixin.bobby_onFakeChunkAdded` demonstram o caminho. Usa palettes de blocos/biomas, luz, heightmaps e entidades de bloco, com arquivos de região via `SimpleRegionStorage`. A chegada de uma chunk real cancela inclusive carregamentos pendentes. Não é necessário reconstruir alocadores GPU para persistência.

Limites identificados: `loadingJobs`, executor de carga com fila padrão e `saveExecutor` não têm um orçamento rígido de bytes; a residência das chunks falsas acompanha a distância de renderização; `save` serializa e grava na saída da chunk e não compara conteúdo antes de regravar. A cópia rasa é adequada à chunk descarregada, não à gravação periódica de uma chunk ainda mutável. Não fornece por si só geração visual antecipada nem prova que milhões de chunks possam ser reapresentadas com RAM pequena. Essas observações se referem ao código examinado, não a um benchmark comparativo.

Fonte: https://github.com/Johni0702/bobby (checkout de referência em `upstream/bobby`).

**Voxy** separa `WorldEngine`, `SectionSavingService`, `SectionSerializationStorage` e `StorageBackend`. O padrão em `StorageConfigUtil` é RocksDB com Zstd nível 1. `exchangeIsInSaveQueue` coalesce gravações; o limite de 5.000 é soft e pode transferir trabalho ao produtor, não uma garantia de orçamento de frame. O dado persistido é voxel/seção com níveis, não endereço GPU. O seu renderer e modelo de dados não são um plugin de restauração para Nvidium. `SaveLoadSystem3` contém TODO de hash: não presumir checksum por analogia.

Fonte: https://github.com/MCRcortex/voxy (`upstream/voxy`).

**Distant Horizons** mantém dados LOD em SQLite (`FullDataSourceV2Repo`, esquema `0020-sqlite-createFullDataSourceV2Tables.sql`), com coordenadas/nível, checksum, formato, compressão e timestamps. `AbstractDelayedSaveCache` agrupa alterações; `ChunkUpdateQueueManager` limita e prioriza atualizações. `WorldGenerationQueue` distingue fila e geração ativa, prioriza posições e consome resultados por uma interface de geração. São referências úteis de armazenamento, reconciliação e agendamento, mas o formato LOD e o renderer próprios não conservam automaticamente a geometria normal do Nvidium. Geração DH não deve ser transplantada sem examinar os wrappers da versão alvo.

Fontes: https://gitlab.com/distant-horizons-team/distant-horizons-core e https://gitlab.com/distant-horizons-team/distant-horizons (`upstream/distant-horizons*`).

**Nvidium e forks:** branch oficial 26.2 fixado em `bda9ff481a6a1811bacdf623be864b033055f321`, Nvidium `0.4.4-beta6-26.2`, Sodium `0.9.2+mc26.2`. Inspecionados também `drouarb/nvidium` branch 26.2 e `OPS-NeoRetro/Meshium`. A pesquisa de forks populares e de repositórios com “nvidium persistent” não encontrou uma implementação completa do objetivo. Isso não é uma auditoria exaustiva de todos os forks. Nos checkouts inspecionados, “Persistent” refere-se a buffers OpenGL mapeados, não a persistência de terreno em disco. O mecanismo de retenção de regiões do Nvidium vale para a sessão e está sujeito à expulsão por VRAM.

## Comparação de caminhos

| Caminho | Evidência | Custo / risco |
|---|---|---|
| Snapshot de blocos, biomas e luz → chunk visual → Sodium → Nvidium | Bobby demonstra hooks de chunks e `ChunkTrackerHolder`; Nvidium recebe a saída normal do mesher | Precisa reconstruir malha; vizinhos e luz são essenciais; RAM depende da janela ativa |
| Persistir `RepackagedSectionOutput` | `SectionManager.uploadChunkBuildResult` contém geometria, offsets e limites | Formato de vértices, atlas de textura, índices translúcidos, configurações e versões acoplados; dados nativos têm vida curta |
| Persistir endereços/alocadores GPU | Nenhuma necessidade demonstrada | Endereços não sobrevivem à sessão; rejeitado |
| Usar renderer Voxy/DH | Persistência existente | Muda a experiência visual e a integração alvo |

**Decisão provisória:** implementar um mod independente com snapshot visual e reapresentação pelo pipeline normal. Bobby, Voxy e DH são referências, não dependências obrigatórias. Manter armazenamento e filas em Java independente do Minecraft; concentrar hooks em `fabric/`. SQLite transacional evita criar já um formato de regiões e compactação próprios. Começar com DEFLATE como baseline disponível no JDK, medir antes de eleger codec final.

Não armazenar entidades ou estado de simulação no primeiro protótipo. Não reter chunks falsas ilimitadamente. Dados reais sempre vencem. Preservar o renderer, mesher, atlas e alocação de Nvidium/Sodium. A geometria pronta será medida como alternativa, não usada como formato canônico nesta etapa.

## Experimentos necessários antes de consolidar

1. Testes determinísticos de corrupção, transação interrompida, deduplicação, coalescing, fila cheia, troca de mundo e cancelamento de leitura obsoleta.
2. Comparar em uma cena fixa: Minecraft+Sodium+Nvidium, os mesmos com Bobby, e o protótipo. Registrar frame times, RAM, bytes em disco, tempo disco→primeira malha e horizonte completo.
3. Reiniciar, retornar a área já vista, editar bordas, explodir terreno, alterar iluminação, trocar dimensão e resource pack.
4. Medir ampliação de raio: uma janela de chunks CPU convencional tem custo quadrático. O protótipo não prova ainda streaming de milhões de chunks ou reconstrução fora do raio normal. Só então considerar janelas temporárias de meshing ou um cache derivado de geometria.
5. A geração antecipada será integrada depois de validar a reconstrução. Pedidos devem entrar no scheduler do servidor integrado; não chamar worldgen vanilla em threads arbitrárias nem regenerar servidor remoto por seed.

## Limites de evidência

O teste integrado executou com uma RTX 3060, Sodium 0.9.2 e Nvidium 0.4.4-beta6, cliente com distância 8 e servidor integrado limitado a 2 no cenário de restauração. Confirmou snapshots restaurados passando por `SectionManager.uploadChunkBuildResult`, substituição por chunks reais e um bloco alterado para diamante persistindo após uma segunda reabertura. Isso valida o caminho de dados, não equivalência visual completa ou desempenho em grande escala. O limite do servidor é aplicado por mixin exclusivo do teste, ausente do JAR distribuído.

Em uma execução anterior foram observadas 101 chunks restauradas e 54 seções enviadas ao Nvidium; 49 snapshots idênticos evitaram regravação. Esses contadores variam com o mundo e o agendamento. A métrica de primeira malha começa na instalação da chunk visual; não representa latência total disco→imagem nem horizonte completo. Não houve benchmark comparativo controlado contra Bobby, Voxy ou DH.

Versões 26.1.2 e 26.3 continuam requisitos futuros; não declarar compatibilidade usando apenas metadados. Geração antecipada continua pendente. A inspeção de `ServerChunkCache.getChunkFuture` encontrou bloqueio gerenciado no ramo chamado pela thread do servidor: o nome assíncrono não basta para justificar uma integração sem bloqueios.

## Interface de configuração

A integração usa o entrypoint `modmenu` e `ConfigScreenFactory` oficial. Cloth Config 26.2.155 fornece lista pesquisável, sliders, campos, restauração de padrões e ações de cancelar/salvar. A tela edita uma cópia; salvar persiste JSON com substituição atômica quando disponível. Alterações feitas no mundo são aplicadas na próxima troca de sessão, evitando mudar identidade ou orçamento de chunks em uso. A interface mostra apenas funções implementadas e valida a relação entre debounce e idade máxima. Português brasileiro e inglês incluídos.

## Correção após o teste do usuário: altitude e alcance

Na instalação real, a alpha 1 registrou 120 chunks restauradas após reabrir e zero seções restauradas enviadas ao Nvidium. A configuração do cache era raio 67, mas `CacheSession` restringia o raio a `min(67, renderDistance + 1)`, efetivamente 9. O teste anterior em terreno plano perto do solo não cobria a reconstrução de malhas fora da seleção de visibilidade do Sodium.

A alpha 2 usa o raio configurado no cache e acrescenta um produtor limitado de tarefas na saída de `RenderSectionManager.submitDeferredSectionTasks`. Ele respeita `ChunkJobCollector` e `UploadResourceBudget`, submete somente builds/rebuilds pendentes, e reutiliza `submitSectionTask`. Um cursor espacial constante em memória percorre posições de seções independentemente da altitude/direção da câmera. Não restaura GPU nem chama worldgen. A integração depende especificamente das APIs internas do Sodium 0.9.2, verificadas no JAR usado pela compilação.

A compatibilidade do cache deixa de depender da versão deste mod, preservando o número do formato do snapshot e as demais verificações. Um fallback reconhece a identidade exata usada pela alpha 1 para reutilizar seus arquivos. Isso não reúne automaticamente caches com conjuntos diferentes de outros mods.

## Alpha 3: retirar decodificação do cliente e corrigir capacidade

O caso real tinha 3725 registros no momento da auditoria, mas a alpha 2 cobrava pelo menos 1 MiB estimado por chunk de 24 seções. O orçamento de 512 MiB limitava a residência a 512, independentemente do raio 67. A nova estimativa usa `65536 + max(rawBytes * 4, sectionCount * 4096)`; aplicada ao corpus auditado, passa de 3725 MiB estimados a aproximadamente 1196 MiB. Isso corrige superestimação, não representa uma redução física comprovada do tamanho de cada objeto.

Codecs vinculados aos registros são capturados no cliente e usados somente para leitura no worker. O worker lê, verifica, descomprime e decodifica palettes e luz em um snapshot preparado, sem carregar `Level` ou referências do renderizador. A criação da `VisualChunk`, instalação e notificações de renderização permanecem na thread cliente. Há no máximo oito snapshots preparados na fila, com transferência exclusiva de propriedade. Exceções de decodificação retornam junto ao ticket e são rejeitadas individualmente, sem parar o worker.

O cliente instala até oito snapshots por tick por padrão, sujeito ao orçamento compartilhado; a interface permite 1–16. O mesher usa até quatro tarefas por frame por padrão, ainda sujeito ao orçamento de jobs/uploads do Sodium e ao prazo cooperativo de 0,5 ms. Colunas ausentes são puladas integralmente. A seleção de candidato mais distante deixa de percorrer o mapa novamente para cada chave candidata quando a capacidade está cheia.

Validação quantitativa com uma cópia do terreno real e limites de evidência em [validation/README.md](validation/README.md). O modelo continua próximo ao Bobby: residência CPU limitada de chunks visuais e meshing normal; ainda não há uma janela transitória de meshing que permita restaurar todo um horizonte arbitrário mantendo poucos chunks na CPU.
