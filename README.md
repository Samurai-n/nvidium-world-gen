# Nvidium World Cache

Mod experimental para Fabric que guarda terreno recebido pelo cliente e o reapresenta ao pipeline normal do Sodium e do Nvidium. No modo World Gen, também pode gerar terreno em mundos locais e preparar essas chunks para aparecerem no Nvidium.

## Situação

- Alpha atual: **0.1.0-alpha.12 para Minecraft 26.2**.
- O World Cache preserva terreno que o cliente já recebeu.
- O World Gen gera chunks reais em mundo local; não é compatível com geração em servidor remoto.
- O terreno armazenado é visual. Ele não mantém entidades nem simula chunks descarregadas.
- É um projeto independente e experimental, sem vínculo oficial com Nvidium, Sodium, Modrinth ou CurseForge.

Faça backup dos seus mundos antes de testar versões alpha. O mod grava o mundo gerado normal do Minecraft e guarda snapshots visuais em um banco separado.

## Instalação

Instale em um perfil Fabric com Minecraft **26.2**, Java **25**, Fabric Loader **0.19.3+**, Fabric API **0.153.0+26.2**, Sodium **0.9.2+mc26.2** e Nvidium **0.4.4-beta6-26.2**. O Nvidium exige hardware compatível.

As builds alpha são experimentais. Mod Menu e Cloth Config são opcionais e habilitam a interface de configuração. Sem eles, use `config/nvidium-world-cache.json`.

## Compilar

Com JDK 25 instalado:

```powershell
./gradlew.bat build
```

O JAR instalável fica em `build/libs/`. Os testes e builds automáticos rodam em ambiente isolado pelo GitHub Actions.

## Desenvolvimento e releases

As notas e os artefatos das alphas são preservados em `releases/alpha-N`. Para as próximas versões, a automação publica no GitHub, Modrinth e CurseForge quando uma release do GitHub for marcada como publicada e as credenciais estiverem configuradas como secrets.

Veja [como preparar e publicar uma versão](docs/PUBLISHING.md) e as [notas da alpha 12](releases/alpha-12/alpha12.md).

## Licença

A licença do código ainda será definida pelo mantenedor. Até essa definição, não presuma permissão para reutilizar ou redistribuir o código-fonte.
