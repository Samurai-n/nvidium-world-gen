# Alpha 11 — geração em segundo plano

Com a opção **Gerar com jogo pausado ou em segundo plano** ligada no Mod Menu, o servidor de um mundo local continua executando ticks enquanto houver uma tarefa ativa de World Gen, inclusive com o menu de pausa aberto ou a janela sem foco. O cliente mantém o menu de pausa; não altera a opção global do Minecraft de pausar ao perder o foco. Ao concluir, pausar ou interromper o World Gen, a pausa normal volta. Não se aplica a servidores remotos nem ao modo Somente exploração.

O mundo continua ativo durante esse trabalho: tempo passa, criaturas se movem e máquinas funcionam. A opção vem ligada por padrão e pode ser desligada no Mod Menu.

Validação em mundo descartável: geração de 9 chunks com menu de pausa aberto, gravação e restauração ao Nvidium; teste de World Gen, dimensões e deslocamento em `build/alpha11-worldgen-fixed.log`.
