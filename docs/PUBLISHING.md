# Publicação

A alpha 15 é a última alpha planejada. Ela reúne quatro JARs distintos, para Minecraft 1.21.11, 26.1.2, 26.2 e 26.3. Cada arquivo deve ser oferecido apenas para a versão indicada no nome. Os ports 1.20.1 e 1.21.1 foram cancelados para esta publicação.

O código da 26.2 fica em `main`; os demais ports ficam nos branches `port/<versão>`. Os JARs e fontes preservados, as notas e os SHA-256 estão em `releases/alpha-15/`. O workflow da tag `v0.1.0-alpha.15` compila os quatro branches, confere os arquivos arquivados e publica uma prerelease no GitHub com os quatro JARs instaláveis. Se `MODRINTH_PROJECT_ID` e `CURSEFORGE_PROJECT_ID` estiverem definidos, também envia cada JAR às duas plataformas. Os tokens já estão nos secrets do GitHub e nunca devem entrar no repositório.

Para publicar nas plataformas, crie primeiro os projetos Modrinth e CurseForge e configure os IDs nas variáveis do repositório. Os tokens precisam ter permissão de criar versões. Confira nome, imagem, licença MIT, descrição, dependências e versões de Minecraft em cada página. Depois envie a tag. O workflow pode ser reexecutado após configurar os IDs, se o GitHub já tiver sido publicado.

A release é experimental. As notas em `releases/alpha-15/README.md` registram o impacto de FPS observado e o fato de que World Gen opera apenas em mundos locais. Não prometa desempenho instantâneo ou compatibilidade com versões que não foram validadas.