# Alpha 8 — World Gen acompanha o jogador

Esta versão acelera a geração local com até quatro pedidos de chunk simultâneos, conforme o preset ou a velocidade escolhida. O padrão é dois. Cada pedido usa o pipeline normal do servidor integrado; o mod congela no máximo uma chunk por tick e grava snapshots pelo worker do cache. Quando o servidor fica lento, reduz a frequência dos pedidos e a concorrência. O intervalo se recupera após pausa do jogo. Isso não torna a geração instantânea: biomas complexos, dependências de chunks e disco ainda custam tempo.

O World Gen agora acompanha deslocamentos e teleportes na dimensão atual. Espera alguns ticks no destino para priorizar o cache visual, encerra a tarefa anterior e inicia uma nova área ao redor do jogador. Pausa ou parada manual impedem esse acompanhamento. Os dados já gerados permanecem no save e no cache. Não há compartilhamento entre jogadores nem geração em servidores remotos.

A página gráfica mostra separadamente a distância para **exibir terreno salvo** e o raio para **gerar terreno novo**. Raio de geração 16 cobre até 1.089 posições; raio 128 cobre até 66.049. Presets e velocidade agora ajustam também o ritmo da geração. O Mod Menu contém o controle técnico de pedidos simultâneos e a opção de acompanhar o jogador. Mudanças de configuração entram após sair e reabrir o mundo.

Os limites de cache no SSD e a reserva de espaço continuam cooperativos; o save vanilla cresce à parte. O cache em RAM é estimado, não uma quota do heap ou da VRAM. O servidor vanilla pode carregar chunks vizinhas para cada pedido. Use /nvidium world gen status para ver pedidos, centro, ritmo e progresso.

Validação: testes unitários e cliente real Cloth/Sodium/Reese's; geração, persistência e restauração em Overworld/Nether/End; teleporte, pausa, retomada e parada em mundos descartáveis. Logs em build/alpha8-full.log e build/alpha8-ui-final.log. A velocidade e o FPS da partida do usuário ainda dependem de teste no mundo real; não foram medidos nesta versão.

Instalada no perfil Fabulously Optimized, com um único JAR ativo e configuração preservada. A alpha7 permanece desativada no mesmo perfil. SHA256 do JAR: 4E9F184BAAE5934B11B5BCBB69F7FF152667024D5F9EA10A8C4DAD07864040F6.
