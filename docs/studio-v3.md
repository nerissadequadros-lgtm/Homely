# Homely Studio v3 — alvo 0.5

A especificação v3 enviada em 22/09 substitui o recorte anterior: modos A/B/C independentes, grade configurável/livre, canvas fixo, RGB, posições X/Y, widgets ricos, fontes e gestos.

## Decisões de plataforma
- A: consulta isRequestPinShortcutSupported antes de pedir a confirmação do launcher. O ícone original não é removido. Compatibilidade de icon packs é identificada por launcher conhecido; desconhecidos são apresentados como não confirmados, pois não existe uma consulta Android universal. Publicação de icon pack continua no roadmap azul.
- B: serviço iniciado pela ação explícita do usuário enquanto o app está visível, foregroundServiceType specialUse, canal de notificação com Pausar por hoje. Permissão de sobreposição, notificação e acesso de uso solicitados em telas explícitas. O último aplicativo em primeiro plano é consultado localmente para limitar o overlay ao launcher; não se envia histórico à API. O serviço é ocultado com tela apagada/bloqueada e em outros aplicativos. Não é reiniciado automaticamente no boot.
- C: somente Aplicar tema completo solicita o papel HOME. Preferências A/B/C não apagam rascunhos e não trocam silenciosamente o launcher.
- Fonte TTF/OTF até 5 MB, armazenada no tema e em cópias privadas. Relógios de AppWidgetProvider usam TextClock para atualização contínua; fontes customizadas estáticas são rasterizadas. Fontes próprias no relógio desenhado dentro do Homely Home não têm essa restrição.
- Bloquear a tela por gesto usa DevicePolicyManager com autorização explícita limitada a force-lock. Sem essa autorização, a ação explica como habilitá-la; não mostra uma tela falsa.

Referências oficiais: [atalhos](https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts), [serviços em primeiro plano](https://developer.android.com/develop/background-work/services/fgs/service-types), [restrições de inicialização](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

## Recursos implementados na 0.5

- Escolha A/B/C no primeiro acesso ao Studio; revisão em Perfil → Configurações → Modo de uso.
- Editor de ícone com canvas fixo, imagem original preservada, sete máscaras, espelhamento, sombra/borda, arrastar, pinça e rotação. Painel inferior com RGB/HEX, nudge X/Y de 1 dp, texto, fontes, grade de 2 a 12 divisões e posição livre.
- Editor de widget com 15 tipos de item: texto, título, relógio digital, analógico, data, bateria, anel de bateria, imagem, atalho de app, forma, nota, respiro, progresso, contagem e prévia de clima. Até 16 camadas por widget e 12 widgets por tema, com tamanho, posição, opacidade, fonte, peso, alinhamento e ordem independentes.
- Arrastar e redimensionar pelo canto; grade opcional de 2 a 12 divisões por eixo; zoom/pan com dois dedos. Desfazer/refazer, rascunho automático e fontes próprias TTF/OTF.
- Densidade do compositor por pinça; kit inicial posicionado com arrastar; ações de segurar área vazia/ícone, deslizar e toque duplo. Bloquear a tela é opcional e pede autorização de administrador **somente para bloquear** — não substitui a tela de bloqueio.
- Formato 1.4 copia fontes, originais de ícones, imagens das camadas e todas as propriedades para importar, exportar, publicar ou aplicar privadamente.

## Limites desta entrega e próximos passos

A parte azul da especificação continua no roadmap: icon pack publicado para launchers externos, IA de remoção de fundo, máscara SVG, guias magnéticas, seleção em grupo, root e tela de bloqueio própria. O clima é uma prévia identificada, sem dados meteorológicos. Calendário mensal, contatos e mini dock são futuras extensões da biblioteca. A barra de status do Android não é alterada.

O modo B mantém um widget flutuante ativo por vez nesta versão; ativar outro substitui o anterior. O conteúdo é ocultado em outros apps e na tela bloqueada; a identificação da home depende dos eventos de uso fornecidos pelo Android/fabricante. Ao pausar, o serviço para e precisa ser reativado explicitamente. Não solicita exclusão de otimização de bateria nem inicia no boot.

Fontes próprias são preservadas e renderizadas nos textos e no Homely Home. Relógios de widgets nativos/flutuantes usam a fonte nativa do TextClock para continuar atualizando sem um serviço extra. Não há promessa de alterar o ícone original no launcher do fabricante: o modo A solicita um novo atalho, com confirmação do sistema.

