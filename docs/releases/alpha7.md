# Alpha 7 — World Gen local

Dois modos: Somente exploração guarda o terreno recebido; World Gen gera terreno real no servidor integrado e envia snapshots para o cache. World Gen é o padrão, com raio de 16 chunks ao redor da posição ao entrar na dimensão e intervalo de 5 ticks entre pedidos. Uma tarefa cobre um quadrado finito de 1.089 chunks nesse raio; não acompanha o jogador indefinidamente. Outro ponto pode iniciar outra tarefa pelo chat. Trocar de dimensão ou sair interrompe a tarefa; entradas futuras iniciam novamente a área, reutilizando terreno já existente no save.

Há um pedido de chunk completa em andamento, no máximo um snapshot esperando gravação e confirmação do worker antes de avançar. Tickets apenas de carregamento são liberados após copiar a chunk. O caminho privado getChunkFutureMainThread é usado por um invoker26.2: a API pública getChunkFuture faz managedBlock na thread do servidor. A geração usa o pipeline normal do Minecraft, inclusive suas dependências de chunks vizinhas; um pedido não significa que apenas uma chunk física fica na RAM. O agendador aguarda se o servidor demora ou se sobra menos de256MiB/10%do heap.

Proteções: orçamento global de 8 GiB para bancos do cache e reserva de 4 GiB livres. O worker verifica arquivos SQLite/WAL/SHM a cada segundo, com margem de 64 MiB; interrompe novas gravações/geração sem apagar dados, enquanto leituras continuam. São limites cooperativos, com possível crescimento entre verificações; não quotas exatas do sistema. O save vanilla não entra no teto do cache. A reserva de espaço é verificada tanto na unidade do cache quanto na unidade do save, inclusive se estiverem em discos diferentes. Geração já em andamento e outros programas podem continuar escrevendo. A pressão da RAM não inclui a VRAM, que segue gerenciada pelo Nvidium.

Comandos:

- `/nvidium` ou `/nvidium help`: ajuda.
- `/nvidium world cache`: status; ações `status`, `pause`, `resume`.
- `/nvidium world gen`: status; ações `status`, `start [raio]`, `pause`, `resume`, `stop`.
- Exemplo: `/nvidium world gen start 16`. Raio de1a128, na dimensão e posição atuais. `start` inicia/reinicia uma área; `resume` retoma uma tarefa pausada. Cache pausado também impede geração.

Mod Menu inclui geração e armazenamento. Página gráfica mantém controles simples, acrescentando apenas o modo. Dicas do Mod Menu têm largura limitada e aparecem abaixo do cursor, acima se faltar espaço na parte inferior, apenas na tela deste mod.

Multiplayer remoto continua cache de terreno recebido. Compartilhar cache do host/LAN fica para etapa futura, com protocolo, identidade de mundo e limites de banda/processamento; não implementado nesta alpha.

Instalada no perfil Fabulously Optimized, preservando a configuração e todas as alphas anteriores. SHA256: 1F36C6015368C78E80622CFE895DF4AF8EB374275867E09E57DAF5F52BCE2BCE.

Validação: 22 testes JUnit, interface real Cloth/Sodium/Reese's, comandos Brigadier, geração e persistência passaram em mundos descartáveis. Nove chunks do Overworld geradas sem recebimento pelo jogador foram gravadas/restauradas e chegaram ao Nvidium. Nether e End passaram em geração/gravação/restauração de uma chunk por dimensão. A chunk do End pode conter vazio, portanto esse teste não exige geometria visível. Os limites de tempo são cooperativos e houve ultrapassagens ocasionais; não há garantia de FPS constante. Logs: build/alpha7-release-tests.log e build/alpha7-dimensions.log. Dica revisada visualmente em docs/validation/alpha7-tooltip-pt.png.
