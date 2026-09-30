# Publicação

A alpha 15 é a última alpha planejada. A [prerelease no GitHub](https://github.com/Samurai-n/nvidium-world-gen/releases/tag/v0.1.0-alpha.15) já está pública com quatro JARs para Minecraft 1.21.11, 26.1.2, 26.2 e 26.3. Os quatro builds e testes do GitHub Actions passaram. Cada arquivo serve apenas para a versão indicada no nome; 1.20.1 e 1.21.1 não foram publicados.

O código da 26.2 fica em `main`; os outros ports ficam nos branches `port/<versão>`. Os JARs, fontes, notas e hashes SHA-256 estão em `releases/alpha-15/`. A tag da release é `v0.1.0-alpha.15`. O workflow também pode ser iniciado manualmente: ele repete os builds e mantém a release do GitHub, se ela já existir.

## Modrinth e CurseForge

Os tokens estão nos secrets do repositório, mas os projetos e seus IDs ainda não foram confirmados. Quando as páginas existirem, defina `MODRINTH_PROJECT_ID` e `CURSEFORGE_PROJECT_ID` em **Settings → Secrets and variables → Actions → Variables**. Depois execute novamente `Publish alpha 15` no GitHub Actions. Cada plataforma tem um job independente, que só roda quando seu ID está definido. Confira nome, imagem, licença MIT, descrição, dependências e versões de Minecraft em cada página. Nunca coloque tokens no repositório.

A release é experimental. As [notas](../releases/alpha-15/README.md) registram o impacto de FPS observado e o limite do World Gen a mundos locais. A logo e as capturas ficam a cargo do autor.