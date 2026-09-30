# Primeira release pública — critérios

**Alvo proposto:** `0.1.0-alpha.14+26.2`, marcado como **experimental**. Não usar “final” nem prometer velocidade instantânea. Manter C2ME opcional: o mod deve funcionar com e sem ele.

1. **Fechar a alpha 14.** Decidir se a instrumentação de tempos em `WorldGenerator` fica; revisar o diff local; compilar o JAR instalável e o sources JAR; arquivar ambos e notas em `releases/alpha-14/`. Nenhum JAR da alpha 14 está instalado ou publicado neste momento.
2. **Validar comportamento.** Testes unitários/Gradle, GameTests de cache, World Gen e interfaces em mundos descartáveis. Em sessão real, verificar restauração ao reentrar, geração perto do jogador e após teleporte, pausa/segundo plano, troca de presets sem reiniciar, FPS, RAM e crescimento do cache/disco. Registrar limites observados; não escrever nos mundos do usuário durante testes automatizados.
3. **Preparar apresentação.** Usar a logo original em arquivo local como ícone do mod e das páginas. Selecionar uma captura real de terreno restaurado e, opcionalmente, uma demonstração curta sem aceleração. Manter o README em inglês, curto e sem tutorial de instalação. Notas da versão devem explicar World Cache, World Gen local, Minecraft 26.2, Sodium/Nvidium obrigatórios e C2ME opcional.
4. **Preparar plataformas.** Criar as páginas do projeto no Modrinth e CurseForge; colocar os IDs nas variáveis `MODRINTH_PROJECT_ID` e `CURSEFORGE_PROJECT_ID` do GitHub. Secrets já existem. Confirmar que os requisitos exibidos nas páginas concordam com `fabric.mod.json`.
5. **Publicar.** Fazer commit/push da versão fechada e só então enviar a tag `v0.1.0-alpha.14+26.2`. O workflow compila e publica em GitHub, Modrinth e CurseForge; acompanhar o resultado e conferir arquivos, versão, dependências e links nas três páginas. Preservar todas as alphas anteriores.

**Condição para a tag:** itens 1–4 concluídos e JAR da mesma versão validado. Se algum teste revelar falha, corrigir numa versão seguinte antes de etiquetar.
