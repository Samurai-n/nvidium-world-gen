# Arquitetura do World Cache e World Gen

## Responsabilidades

`ChunkGenerationSource` é a fronteira com o servidor. Recebe uma posição e retorna uma chunk FULL assincronamente. `VanillaChunkGenerationSource` usa o pipeline normal do Minecraft26.2 e possui os tickets da tarefa; `close` libera apenas seus tickets. A interface não conhece SQLite, Sodium ou Nvidium. Uma integração futura com gerador externo precisa respeitar a propriedade desses tickets, o limite de pedidos e a thread do servidor.

`WorldGenerator` escolhe posições próximas primeiro, limita pedidos simultâneos, congela uma chunk por tick e aguarda confirmação da persistência. A concorrência padrão é2, máximo4. `GenerationPacer` usa cadência entre ticks para ajustar pedidos:1tick quando o servidor mantém≤55ms, intervalo configurado proporcionalmente aumentado sob atraso. Isso mede cadência, não tempo de CPU do tick. Geração concluída deixa de trabalhar até haver nova tarefa ou deslocamento.

`ChunkSnapshotCodec` copia a chunk na thread proprietária. O snapshot transferido ao worker não pode ser alterado pelo servidor depois. `CacheWorker` codifica, comprime e grava em SQLite, com limites por quantidade e bytes. `CacheSession` restaura dados preparados para `VisualChunk`, notifica Sodium e dá prioridade aos dados reais recebidos do servidor. Nvidium recebe malhas pelo pipeline normal; não há restauração de VRAM.

## Estado ocioso e prioridade

Buscas espaciais percorrem uma área finita. Movimento ou mudança de distância cria outra busca. Gravações confirmadas acordam as posições correspondentes por uma fila limitada; descarregamento de chunks reais também permite restaurá-las. A fila de eventos contém até256posições e, se saturar, solicita nova varredura finita. O worker dorme sem pedidos; o próximo pedido o acorda imediatamente. Não há varredura periódica dos arquivos de caches quando não existem gravações e o armazenamento está liberado.

O mesher normal do Sodium atende seu trabalho visível primeiro. A busca complementar fora da câmera trabalha por anéis, dentro de0,5ms e dos orçamentos normais do Sodium, e fica ociosa após uma passagem limpa. Novos dados, alterações, uploads, deslocamento e recriação do renderer provocam outra passagem. O cache reduz seu orçamento de instalação para até0,5ms quando a cadência do cliente atrasa. Essas cotas são cooperativas: uma única operação pode ultrapassá-las.

Ocioso não significa custo gráfico zero. Nvidium continua desenhando geometria, mantém sua política de memória e não é controlado pelo limite estimado de RAM do cache. Distância muito grande ainda pode custar FPS/VRAM mesmo sem geração.

## Aplicação de configuração

Salvar/Aplicar publica uma nova configuração durante a partida. Velocidade, limites, geração e opções do cache mudam sem reabrir o mundo. Reduzir distância/RAM remove até4chunks por tick dentro do orçamento; expandir inicia uma busca nova. Limites de armazenamento são observados pelo worker, sem acesso a arquivos na thread gráfica. Mudar namespace ou desligar o cache encerra a sessão visual e seus tickets; a próxima sessão usa a configuração nova. Desconexão descarta referências sem tentar usar um renderer já encerrado.

Pausa/stop manuais são respeitados pelo acompanhamento; mudar explicitamente o modo pode iniciar/parar a geração. Trocar o raio reinicia a tarefa na posição atual. Alterações já aceitas para gravação podem terminar durante o encerramento. Presets permanecem limites manuais úteis; não há promessa de autorregulação perfeita de FPS.

## Compatibilidade e pendências

26.2 é o único adaptador validado. Sodium/Nvidium/Reese's/Cloth são cobertos pelo cliente de testes. Bobby simultâneo continua desativando o cache para impedir dois provedores concorrentes de chunks visuais. C2ME e Chunky são candidatos opcionais: nenhuma integração externa é anunciada como testada. Voxy World Gen V2 é referência de arquitetura, não dependência.

Próximos ganhos a medir: entrega visual sem esperar a releitura SQLite; melhor priorização das posições recém-geradas durante uma passagem longa; aceleração externa em perfil descartável; medições de frame time/picos de heap em terreno complexo. Evitar misturar otimização da geração com a tecnologia LOD do Voxy: o Nvidium precisa de geometria completa.
