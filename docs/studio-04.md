# Homely Studio 0.4 — implementação

O alvo desta versão é o checklist verde da especificação. O aplicativo permanece nativo em Kotlin/Jetpack Compose, com API na VPS e HTTPS por IP na porta 8443.

## Fluxo do Studio
- **Criar → Ícones:** grade de apps, pesquisa, Todos/Modificados/Pendentes, comparação com o original e reset. Editor com upload PNG/JPG/WebP, fundo, escala, rotação, espelhamento, sete máscaras, sombra e contorno. O render usado na prévia gera o mesmo PNG usado no launcher. A origem e as propriedades acompanham o pacote para permitir reedição.
- **Aplicar estilo aos pendentes:** mantém a imagem própria de cada aplicativo e replica fundo, máscara, escala e efeitos. Não substitui todos pelo mesmo logo.
- **Criar → Widgets:** modelos, vários widgets no rascunho, texto/imagem/relógio/data/bateria/atalho/forma, grade 4×4, encaixe ao arrastar, tamanho de camadas, ordem, visibilidade, bloqueio e duplicação. Classes 2×2, 4×2 e 4×4. Cada atalho abre seu aplicativo.
- **Widget Android:** Homely Studio é um AppWidgetProvider configurável. Pode ser adicionado pelo seletor do launcher, com cópia independente da configuração e imagens. Relógio/data usam TextClock; bateria atualiza no ciclo do Android (30 minutos). Launchers compatíveis também recebem solicitação de fixação, sempre com confirmação do usuário. O tamanho final depende da grade e do redimensionamento do launcher.
- **Tema:** mockup, paleta extraída do wallpaper, edição HEX de cinco cores, variantes clara/escura e famílias sans/serif/mono. Exportar/importar .homely, salvar privado, aplicar sem publicar ou publicar na comunidade.
- Histórico de até 50 alterações do rascunho durante a sessão e histórico do editor de ícones. Rascunho gravado localmente após cada alteração; arquivos privados são copiados antes da aplicação.

## Formato 1.3
Compatível com leitura de 1.0/1.1/1.2. Inclui widget.layers, classes de tamanho, paleta secundária/texto, fontFamily, configurações de layout e asset.iconEditor. Recursos icon_source preservam a imagem original; widget_image preserva imagens das camadas. Até 512 recursos, 50 MB por arquivo, 250 MB por pacote, 8 screenshots e 12 widgets com até 16 camadas cada. Importação verifica hashes, tamanho, referências, caminhos e limites do canvas.

Arquivos .hwidget são configurações JSON até 64 KB. Para transportar também imagens, utilize .homely; importação de .hwidget solicita reassociar as imagens.

## Limites e roadmap
A barra real de status, notificações de outros apps e layout de bloqueio não são substituídos. O relógio/bateria opcionais do Homely Home são elementos internos da tela inicial. Para mudar ícones e composição completa, o usuário escolhe o Homely como launcher padrão. Em outros launchers, widgets nativos e atalhos seguem as APIs do Android.

Itens azuis continuam como roadmap: remoção de fundo por IA, SVG customizado, posicionamento livre sem grade, clima/calendário externo, marketplace com curadoria, histórico completo versionado, root/OEM, fontes enviadas e colaboração.

## Referências técnicas
- [AppWidgetProvider e RemoteViews](https://developer.android.com/develop/ui/views/appwidgets)
- [Configuração de widgets](https://developer.android.com/develop/ui/views/appwidgets/configuration)
- [Confirmação de fixação pelo launcher](https://developer.android.com/develop/ui/views/appwidgets/discoverability)
- [Views suportadas, incluindo TextClock](https://developer.android.com/reference/android/widget/RemoteViews)
