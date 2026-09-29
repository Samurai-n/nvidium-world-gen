# Alpha 9 — carregamento ocioso e ajustes ao vivo

- Ajustes são aplicados ao salvar, durante a partida. Distância e limites podem ser alterados sem reabrir o mundo; reduções liberam chunks do cache gradualmente.
- Buscas pelo terreno salvo e pelo terreno já recebido terminam ao cobrir a área. Gravações, mudanças e deslocamento acordam o trabalho necessário.
- A busca complementar de geometria fora da câmera termina após uma passagem sem trabalho. O Sodium mantém sua prioridade para o trabalho visível.
- Geração automática usa pedidos a cada tick quando o servidor mantém o ritmo, dentro dos limites de concorrência/RAM/disco. Sob atraso, reduz o ritmo. O ajuste pode ser desativado no Mod Menu.
- Geração concluída fica ociosa; deslocamentos e teleportes podem iniciar uma nova área, respeitando pausa/stop.
- A fonte de chunks foi separada do agendador e do cache para futuras integrações. Nenhuma dependência de Voxy World Gen V2, Chunky ou C2ME foi acrescentada.

Renderizar terreno continua usando GPU/VRAM, mesmo com cache e geração ociosos. Os limites de tempo são cooperativos e os presets continuam disponíveis. Sem garantia de FPS constante ou de geração instantânea.

Validação concluída:27JUnit; testes reais Cloth/Sodium/Reese's, persistência, geração e restauração nas três dimensões, teleporte/pausa/stop. Leituras de disco e contador da varredura complementar ficaram estáveis após repouso; reduzir/ampliar distância e desligar/religar funcionaram na mesma partida. Logs: build/alpha9-full-fixed.log e build/alpha9-final.log. O teste inicial encontrou cleanup após destruição do renderer; corrigido e revalidado.

Amostra final do agendador sobre as mesmas9chunks já existentes:2,85s manual e1,69s automático; outra execução mediu4,14s e0,54s. A variação impede tratar isso como ganho universal. Não mede geração de terreno novo nem FPS do mundo do usuário. Houve ultrapassagens pontuais dos orçamentos cooperativos.

Instalada no perfil Fabulously Optimized para Minecraft26.2. Um único JAR ativo, alpha8 e alphas anteriores preservadas, configuração copiada byte a byte. SHA256: A51167DCBA3B93478C6765981B10C8A111FE375285D02536CA27581836D55C89.
