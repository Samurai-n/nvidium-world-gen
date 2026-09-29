# Publicação de versões

## O que a automação fará

Ao enviar uma tag `v<versão>` que corresponda à versão no `build.gradle`, o GitHub Actions compila o projeto e executa as verificações do Gradle. Só depois de uma compilação aprovada ele cria a release e envia o JAR instalável para GitHub Releases, Modrinth e CurseForge. O JAR de código-fonte não é enviado como se fosse instalável.

As chaves de publicação ficam nas configurações privadas do repositório, nunca nos arquivos do projeto nem em mensagens.

## Configuração necessária uma vez

Depois de criar os projetos no Modrinth e CurseForge e o repositório no GitHub, abra **Settings → Secrets and variables → Actions** e cadastre:

**Variables**

- `MODRINTH_PROJECT_ID`: identificador do projeto no Modrinth.
- `CURSEFORGE_PROJECT_ID`: identificador numérico do projeto no CurseForge.

**Secrets**

- `MODRINTH_TOKEN`: crie um PAT na página de tokens do Modrinth com somente a permissão **Create versions**. A automação não altera dados do projeto nem destaca versões.
- `CURSEFORGE_TOKEN`: gere um API token na página **My API Tokens** do CurseForge.

Não envie nem cole os tokens em issues, commits, arquivos, capturas de tela ou nesta conversa. Cole cada token diretamente no campo de secret correspondente do GitHub.

## Como publicar uma versão nova

1. Atualize a versão em `build.gradle` e escreva as notas em `releases/alpha-N/`.
2. Faça commit e envie as alterações para o repositório.
3. Crie e envie uma tag igual à versão, com `v` no começo. Para `0.1.0-alpha.13+26.2`, a tag é `v0.1.0-alpha.13+26.2`.
4. Acompanhe **Actions** no GitHub. Falha no build ou configuração ausente interrompe a publicação.

A tag enviada é o sinal de que aquela versão está pronta para publicação pública nas três plataformas. As alphas antigas continuam arquivadas localmente em `releases/alpha-N`; a automação não republica o histórico automaticamente.

## Antes de abrir o repositório

- Defina o nome do proprietário e do repositório no GitHub e confirme se ele será público.
- Defina a licença do código. O projeto ainda não declara uma licença.
- Adicione a imagem da logo como arquivo ao repositório para que ela possa ser configurada como ícone do mod e das páginas.
- Crie os projetos do Modrinth e CurseForge para obter seus IDs e tokens.
- Revise notas e builds alpha antes de gerar a primeira tag pública.
