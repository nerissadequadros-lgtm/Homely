# Theme Builder · Homely 0.7

O editor de temas tem cinco etapas: Papel de parede, Paleta, Ícones, Elementos e widgets, Revisão. Grade, gestos, animações e comportamento ficam nas configurações recolhidas da revisão. É possível voltar diretamente a qualquer etapa. O histórico de desfazer/refazer e o salvamento automático do rascunho continuam disponíveis.

## Prévia e tema aplicado

`ThemeExperience` é o componente usado tanto no editor quanto no `LauncherActivity`. Ele resolve os gestos com o manifesto atual, mantém o estado de navegação e desenha a tela inicial, a grade e suas páginas. O preview compacto pode ser recolhido, reiniciado ou ampliado. Em telas largas ele fica ao lado das propriedades.

Na prévia, 64 aplicativos de demonstração permitem testar grades com várias páginas. Ícones personalizados também aparecem. Toques abrem uma representação do app; menus, bloqueio e outras ações do Android são simulados dentro da prévia. No launcher, os mesmos eventos são encaminhados aos aplicativos instalados, ao menu de personalização e às integrações nativas. Widgets mantêm seu editor de camadas e o canvas do kit permite arrastar suas posições.

## Grade e gestos

- Grade contínua e personalizada: rolagem vertical, com colunas, ícones, margens e espaçamento ajustáveis; presets compacto, equilibrado e espaçado.
- Páginas: quantidade exata de linhas × colunas por página, navegação horizontal ou vertical e controles visíveis de página.
- Lista: uma linha por aplicativo e rolagem vertical.
- Busca, transparência e cantos do painel; desfoque do wallpaper em Android 12 ou superior.
- Transições de deslizar, dissolver, ampliar ou instantâneas, duração e redução de movimento.
- Swipe nas quatro direções, toque, duplo toque, pressão longa e pinça vinculados a ações. Toques gerais usam o espaço vazio; ícones mantêm o toque para abrir e a ação de pressão longa configurada.

Na grade contínua, o gesto vertical movimenta a lista. Deslizar para baixo quando ela está no topo executa a ação própria da grade. Na grade vertical paginada, deslizar para cima avança e para baixo retorna; na primeira página, deslizar para baixo executa a ação de saída. Gestos laterais configurados também são usados na grade horizontal. Busca, título da grade, botões de abrir/fechar, indicadores de página, atalho para o Studio e indicador inferior podem ser ocultados individualmente. O botão Voltar do Android fecha a grade. Ao esconder os controles, configure os gestos para manter os acessos desejados.

As animações de abertura de aplicativos são solicitadas ao Android, que pode ajustá-las conforme o aplicativo de destino e as preferências do sistema. A duração configurável do editor controla as transições internas da grade e das páginas. O bloqueio de tela exige a autorização de administrador já oferecida no editor. A faixa de status desenhada pelo Homely aceita cor e opacidade; os ícones das barras do Android e a navegação imersiva usam as APIs disponíveis. O painel de notificações do Android permanece controlado pelo sistema; o editor oferece acesso às configurações de exibição.

## Persistência e compatibilidade

Os novos campos ficam em `HomeLayout` e `HomeGestures`, com valores padrão para importar temas existentes. A versão de schema permanece 1.5 porque os campos são aditivos. `effectiveDrawerMode()` preserva os temas antigos com `drawerLayout=list`.

O rascunho, a biblioteca privada, os arquivos `.homely` e o manifesto publicado usam a mesma configuração. A validação Android e a validação Zod do servidor verificam os mesmos intervalos e ações. As mudanças de `server/src/manifest.ts` precisam ser implantadas na API para que a publicação em produção preserve os campos novos. Não é necessária migração do banco: o manifesto já é armazenado como JSONB.

## Validação

- `ThemeNavigationTest`: compatibilidade, serialização, resolução contextual de gestos e rejeição de valores inválidos.
- `ThemeArchiveTest`: exportação/importação do manifesto com os novos campos.
- `ThemeBuilderDeviceTest`: abertura por swipe e duplo toque, grade 4 × 5, páginas horizontais/verticais, rolagem, atualização ao vivo, interações simuladas e biblioteca privada.
- Testes do servidor: validação e preservação dos novos campos. A integração de publicação/leitura/download requer `TEST_DATABASE_URL` de um banco dedicado a testes.

Compilação e verificações locais:

```powershell
./scripts/build.ps1
./android/gradlew.bat -p android :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.homely.ThemeBuilderDeviceTest'
```

O APK de desenvolvimento é gerado em `artifacts/homely-0.8.0-debug.apk`.

## Elementos interativos e identidade global

O cabeçalho fixo usa a marca Homely em todos os destinos, inclusive os quatro fluxos de criação. O menu inferior permanece visível. Barras locais contêm somente as ferramentas e o título de contexto.

Criar Elemento salva componentes independentes na biblioteca privada. Eles podem ser inseridos e editados em qualquer widget, com imagem, fonte, cor, fundo, borda, espaçamento, formato, gesto de toque e pressão longa próprios. Cada camada suporta até 12 estados e 8 áreas de toque normalizadas. A hierarquia é tema → widget → elementos → estados → propriedades/interações. A primeira condição correspondente vence; pressionado e alternado têm prioridade, com padrão como reserva. Os estados podem trocar texto, imagem, cor, fundo, opacidade, escala, rotação, animação e ação. Há uma prévia manual de cada estado.

Fontes de dados: bateria, RAM usada, armazenamento usado, volume de mídia, Wi-Fi, Ethernet/LAN, lanterna, sinal Wi-Fi e CPU do processo Homely. Bluetooth usa os perfis de áudio A2DP/headset e requer permissão em Android 12+. Dados indisponíveis são exibidos como indisponíveis, sem valores inventados. Não é um monitor de CPU global ou de sinal celular.

Wi-Fi/Bluetooth abrem os painéis permitidos pelo Android. A lanterna usa CameraManager com autorização de câmera; volume pode ser ajustado. Animações de dissolver, escala, deslizar, rotação, cor e opacidade são nativas e não dependem de GIFs. O modelo Painel rosa, inspirado na referência visual, usa leituras reais e permanece completamente editável.

No Homely Home e nos widgets flutuantes, dados visíveis são consultados a cada dois segundos, com observação do ciclo de vida. Widgets externos usam RemoteViews: relógios continuam nativos, outros elementos são imagens do estado no momento da atualização. Pressão longa pertence ao launcher externo, que não recebe as subáreas ou transições do Homely. Fontes personalizadas são preservadas no Homely e em textos rasterizados; relógios nativos externos usam fontes do sistema.

Fundos e camadas de imagem aceitam preencher/mostrar inteira, zoom, posição e recorte não destrutivo. A geometria é compartilhada com o renderizador nativo. Arquivos .homely transportam fundos, imagens de estados, fontes e configurações; .hwidget transporta somente a configuração e solicita selecionar novamente a mídia. Pacotes privados de ícones/widgets são copiados ao reutilizar para não depender da permanência dos arquivos originais.

Testes adicionais: InteractiveElementsTest verifica prioridade de estados, limites, serialização e recorte; InteractiveElementDeviceTest verifica toque, pressão longa, subáreas, isolamento da prévia, cópia de mídia e renderização nativa. O teste do servidor interactive-elements cobre validação e referências de imagens de estados.
