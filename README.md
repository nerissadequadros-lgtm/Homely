# Homely

**Seu celular. Seu universo.**

Aplicativo Android nativo em **Kotlin + Jetpack Compose**, com backend próprio na VPS. Versão **0.8.0 de desenvolvimento**, implementada a partir das referências fornecidas: violeta, interface clara/escura, feed visual em duas colunas e cabeçalho e navegação inferior persistentes.

## Entrega

- `artifacts/homely-0.8.0-debug.apk`: APK instalável para testes (Android 8 ou superior).
- `android/`: projeto que pode ser aberto no Android Studio, com Gradle Wrapper.
- `server/`: API TypeScript/Fastify, PostgreSQL, testes e coleção original.
- `brand/`: marca vetorial, ícone 512 px e painel de identidade.
- `docs/`: arquitetura, formato de temas e operação.
- `infra/`: configuração de serviço, HTTPS por IP, backups e renovação de certificado.

O **Criador de Temas** organiza a montagem em cinco etapas, com prévia interativa, microanimações, gestos configuráveis e grade contínua, paginada ou em lista. A prévia e o Homely Home compartilham o mesmo componente de navegação. Veja [o funcionamento e a validação](docs/theme-builder.md).

Detalhes das versões anteriores em [widgets vivos e personalização](docs/live-widgets-06.md) e [Studio v3 e modos de uso](docs/studio-v3.md).

## Funcionalidades implementadas

Feed paginado, busca, categorias, área de ícones, detalhes com composição do tema, recomendações por categoria, downloads separados e pacote completo; contas, sessão protegida pelo Android Keystore, nomes únicos, edição de perfil/foto, curtidas, seguidores, coleções e denúncias.

O **Homely Studio v3** tem canvas fixo, painel de propriedades, edição de ícones com camadas, roda RGB/HEX, arrastar/escala/rotação, widgets com 17 tipos de itens, posição livre ou grade ajustável, fontes TTF/OTF, desfazer/refazer e kit inicial por tema. O formato `.homely` 1.5 guarda os arquivos originais, fontes, propriedades e posições. É possível guardar e aplicar temas privados sem publicar.

A integração nativa permite aplicar wallpaper estático, abrir a confirmação do wallpaper animado, criar atalhos com ícones personalizados e adicionar seis widgets nativos. Inclui 16 wallpapers procedurais originais, 12 ícones em três estilos, 32 screenshots de apresentação e uma animação, todos com licença CC0.

## Servidor configurado

| Recurso | Local |
|---|---|
| API HTTPS | `https://82.29.61.16:8443` |
| Saúde | `GET /health` |
| API interna | `127.0.0.1:3187` |
| Código | `/opt/homely/server` |
| Dados PostgreSQL | banco e usuário `homely` |
| Arquivos | `/var/lib/homely/media` |
| Configuração privada | `/etc/homely/api.env` |
| Serviço | `homely.service` |
| Backup local | `/var/backups/homely` |

A API usa certificado emitido por uma autoridade privada do Homely, confiada **somente para esse IP pelo aplicativo**. A verificação de certificado e nome/IP continua ativa. Navegadores comuns não confiam automaticamente nessa autoridade; isso não impede o funcionamento do app. Um domínio com certificado público pode ser adotado posteriormente.

A senha SSH fornecida **não está no código, APK ou arquivos de configuração do projeto**. A senha do banco foi gerada diretamente na VPS. Apenas o certificado público da autoridade acompanha o app.

## Compilar

Requisitos: JDK 17, SDK Android 36, Build Tools 36.0.0.

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Para o teste de integração executado em dispositivo/emulador:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Esse teste cria uma conta temporária na API configurada, verifica sessão/curtidas/coleções e a exclui em `finally`.

No ambiente desta entrega, as ferramentas portáteis estão em `.tools/`; `scripts/build.ps1` prepara as variáveis automaticamente. `.tools`, caches, dependências e segredos não devem entrar no controle de versão.

## Backend em desenvolvimento

Copie `server/.env.example` para `server/.env` e configure um PostgreSQL próprio.

```sh
cd server
npm ci
npm run migrate
npm run seed
npm run dev
```

`npm test` executa os testes unitários. Para os testes de integração, crie um banco separado, migre-o e defina `TEST_DATABASE_URL`. Não use o banco de produção como banco de teste. Na VPS existe `homely_test` dedicado a isso.

## Compatibilidade e escopo

Esta é uma base funcional, não uma versão pronta para lançamento na Play Store.

- O Android permite aplicar wallpapers e solicitar atalhos/widgets. O Homely Home aplica ícones e widgets em sua própria tela inicial, após escolha como launcher; a barra de notificações de outros apps/sistema não pode ser redesenhada por um app comum. A prévia do Studio não promete essas mudanças.
- Modo A fixa atalhos com a imagem editada no launcher atual, mediante confirmação. O ícone original permanece; icon packs externos são uma evolução. A compatibilidade exibida é uma identificação de launchers conhecidos, não uma garantia de integração do Homely.
- Modo B mantém um widget flutuante sobre a home nativa, com permissão de sobreposição, acesso de uso local e notificação de pausa. Widgets nativos do Studio também podem ser adicionados em launchers compatíveis. Modo C aplica o tema no Homely Home, após escolha explícita como tela inicial.
- As recomendações atuais usam categoria e recência. Personalização por comportamento e busca semântica são evoluções.
- Anúncios reais dependem da conta e dos identificadores do provedor; nenhum SDK de anúncios ou anúncio fictício está ativo.
- Recuperação/verificação de e-mail requer um serviço de envio. Ainda não há chat, painel de moderação ou integração com launchers de terceiros.
- Para lançamento: chave de assinatura de produção, políticas públicas, moderação operacional, provedor de e-mail, anúncios e testes em aparelhos/launchers reais. Os backups atuais estão na mesma VPS; destino externo ainda precisa ser definido.

Veja [arquitetura](docs/architecture.md), [formato dos temas](docs/theme-format.md), [operação](docs/operations.md) e [validação](docs/validation.md).
