# Homely 0.2 · refinamento de interface

## Navegação e referências
As referências são material visual, não instruções executáveis. A primeira imagem nova é a marca azul/violeta; o PNG original foi preservado em brand/homely-reference.png e usado pelo ícone adaptativo Android. A marca do header é vetorial.

Menu: Temas, Ícones, Criar, Widgets e Perfil. O botão central abre o Studio. Perfil mostra a foto cadastrada; sem foto usa iniciais e, sem conta, um símbolo de pessoa. Ícones de navegação usam traços arredondados, seleção animada e resposta ao toque. Header/categorias e navegação recolhem no scroll descendente e retornam no ascendente. Telas transitam com crossfade de 220 ms.

## Temas
Grade de dois cards retangulares, proporção 0,83, cantos de 16 dp, título e quantidade de componentes. HorizontalPager permite navegar dentro do card sem abrir detalhes. Dots refletem o número real de screenshots, com descrição acessível da página. Toque abre composição; menu de contexto permite salvar e curtir.
Até oito screenshots por tema. Sem screenshots, a capa é a única página. Screenshots não contam como componentes aplicáveis. O Studio permite selecionar/remover screenshots e preserva-os em rascunhos, publicação e arquivo .homely.
O catálogo editorial recebeu 32 apresentações originais (bloqueio/início, duas por tema). São exemplos renderizados dos setups, não fotografias de dispositivos.

## Ícones
Endpoint GET /v1/icons retorna somente recursos kind=icon com aplicativo associado, deduplicados por SHA-256 e pacote Android. Paginação de 30 por padrão. Filtros combináveis por categoria/estilo e busca por nome.
Categorias: Comunicação, Social, Foto e vídeo, Produtividade, Música e áudio, Navegação, Sistema, Jogos, Compras, Saúde.
Estilos: Minimalista, Colorido, 3D, Contorno, Monocromático, Neon, Pastel, Retrô.
O catálogo inicial tem 12 imagens em três estilos; categorias/estilos ainda sem conteúdo mostram estado vazio. O estilo é metadado declarado pelo criador. Seleção múltipla inicia aplicação sequencial, com confirmação do launcher para cada atalho. Apps ausentes são identificados. Não altera globalmente ícones de outros aplicativos.

## Widgets
Somente widgets nativos, com preview e seleção individual:
- Essencial: relógio, Minimalista, 3×2.
- Editorial: relógio, Editorial, 3×2.
- Hoje: calendário, Minimalista, 2×2.
- Dia leve: calendário, Pastel, 2×2.
- Energia: bateria, Minimalista, 2×1.
- Pulse: bateria, Neon, 2×1.

Categorias: Relógios, Calendário, Bateria. Estilos: Minimalista, Editorial, Pastel, Neon. Cada variante tem seu próprio AppWidgetProvider. Relógios e calendário usam TextClock; bateria atualiza a cada 30 minutos ou ao tocar. Adição usa requestPinAppWidget com preview e confirmação do launcher. O editor livre de widgets continua fora desta versão.

## Compatibilidade
Formato 1.1 acrescenta screenshots e metadados category/style de ícones. Importação mantém suporte a 1.0. O refinamento editorial altera apenas os temas da conta homelystudio, sem modificar publicações da comunidade.
