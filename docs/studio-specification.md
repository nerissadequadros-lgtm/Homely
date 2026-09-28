# Homely Studio — Especificação Avançada
## Criador de Ícones, Widgets e Temas

> Documento de referência para orientar o desenvolvimento (ou um prompt para IA/Claude Code) do módulo **Homely Studio**: o editor visual estilo Canva onde o usuário monta tema, ícones e widgets do zero.

---

## 0. Visão geral

O Homely Studio deve ser o coração criativo do app: um editor visual, em camadas, onde qualquer elemento visível da tela inicial (papel de parede, ícones de apps, widgets, relógio, paleta) pode ser recriado, importado, ajustado e salvo como um pacote `.homely` reutilizável e publicável na comunidade.

A referência de experiência é **Canva**: canvas central, painel de camadas à esquerda/embaixo, painel de propriedades à direita, biblioteca de assets pesquisável, arrastar-e-soltar, desfazer/refazer robusto, e templates prontos para quem não quer começar do zero.

Este documento define dois patamares de entrega para cada bloco de funcionalidade:

| Nível | O que significa |
|---|---|
| 🟢 **Aceitável (MVP realista)** | O que dá para construir com esforço razoável, 100% dentro do que um app Android comum (sem root) pode fazer. Deve ser o alvo da próxima versão. |
| 🔵 **Perfeita (visão avançada)** | O teto de ambição do produto — inclui recursos que dependem de mais tempo de engenharia, parcerias, ou (em casos pontuais) root/OEM. Serve como norte de roadmap. |

---

## 1. Criador de Ícones

### 1.1 Objetivo
Permitir que o usuário substitua o ícone de **qualquer app instalado**, veja de forma clara o que já foi personalizado e o que ainda está no padrão, e crie ícones novos a partir de imagens próprias ou da biblioteca, com controle visual completo — sem precisar sair do editor.

### 1.2 Fluxo de uso
1. Usuário abre "Ícones" dentro do Studio.
2. Vê uma **grade com todos os apps instalados**, com busca e filtros.
3. Toca em um app → abre o **editor de ícone (canvas)**.
4. Escolhe origem da imagem: upload próprio, biblioteca Homely, ou pack já instalado.
5. Ajusta camadas, máscara, cor de fundo, efeitos.
6. Salva → o app entra na lista como "modificado" e o ícone é aplicado no Homely Home.
7. Pode aplicar em lote a vários apps de uma vez (ex.: "aplicar máscara squircle a todos os ícones pendentes").

### 1.3 Editor de ícone (canvas), estilo Canva
- Canvas quadrado com grade de segurança (safe zone) visível, seguindo a especificação de **ícone adaptativo** do Android (108×108dp, área segura 72×72dp central).
- Ferramentas: mover, redimensionar, rotacionar, espelhar, recortar (crop), zoom no canvas.
- Painel de camadas por ícone:
  - **Camada de fundo** (cor sólida, gradiente, imagem, blur de imagem)
  - **Camada de primeiro plano** (a imagem/logo em si, com transparência)
  - **Camada monocromática** (para o modo "Material You" do Android 13+, ícone temático de uma cor só)
- Upload de qualquer imagem (PNG, JPG, WEBP, SVG); recorte automático de fundo (remoção de fundo) como recurso assistido.
- Ajustes finos: brilho, contraste, saturação, sombra projetada, contorno (stroke), espessura e cor da borda, raio de canto quando a máscara for "squircle customizado".
- Biblioteca própria de ícones (mín. 3 estilos × conjunto de apps populares) com busca por nome do app.
- Histórico de versões do ícone (poder voltar a uma edição anterior).

### 1.4 Máscaras e formatos disponíveis
Círculo, squircle, teardrop (gota), hexágono, pentágono arredondado, quadrado com raio configurável, "sem máscara" (formato livre do PNG/SVG enviado), e **máscara customizada** desenhada pelo próprio usuário (upload de um SVG de forma).

### 1.5 Listagem e status dos ícones
Tela dedicada com abas/filtros:

| Aba | Conteúdo |
|---|---|
| **Todos** | Todos os apps instalados, ordenáveis por nome, categoria ou data de instalação |
| **Modificados** | Apps que já têm ícone customizado pelo usuário, com miniatura do antes/depois |
| **Pendentes** | Apps ainda no ícone padrão do sistema — call-to-action "personalizar" em cada linha |
| **Por pacote** | Agrupamento por icon pack aplicado (útil quando o usuário mistura fontes) |

Cada item da lista mostra: ícone atual, nome do app, badge de status (● modificado / ○ padrão), e ação rápida (editar, resetar para padrão, duplicar edição para outro app).

### 1.6 Ícones de sistema (bateria, wi-fi, sinal, relógio da barra de status)
Aqui é essencial ser honesto sobre o que o Android permite:

- 🟢 **Aceitável / realista sem root:** o Homely **não pode substituir os ícones reais da barra de status do sistema** (bateria, sinal, wi-fi, relógio) porque essa área pertence ao **System UI**, controlado pelo Android e não acessível a apps comuns — nem mesmo sendo o launcher padrão. Isso vale para qualquer app da Play Store, não é uma limitação só do Homely.
  - O que **é** possível: o usuário customiza esses elementos dentro da **própria interface do Homely** — por exemplo, um indicador de bateria estilizado como widget na tela inicial, um relógio widget com a tipografia/cor do tema, um mini status "informativo" desenhado pelo app.
  - Também é possível oferecer uma **barra de status "replicada"**, opcionalmente sobreposta via permissão de "exibir sobre outros apps", puramente decorativa — deixando claro na UI que é uma simulação visual e não a barra real do sistema.
- 🔵 **Perfeita / avançada (fora do escopo padrão):** substituição real dos ícones de sistema só é tecnicamente viável com **root + Xposed/Substratum**, ou em parceria com fabricantes que expõem APIs próprias de tema (ex.: Samsung Good Lock/Theme Park, algumas OEMs com motor de tema nativo). Deve ficar marcado no roadmap como "modo avançado opcional", nunca prometido na prévia padrão do Studio.

> Recomenda-se deixar essa distinção visível na própria UI do editor (ex.: um aviso discreto: "ícones do sistema operacional não podem ser alterados por apps comuns no Android — aqui você personaliza a experiência dentro do Homely").

### 1.7 Aplicação em massa e regras automáticas
- "Aplicar a todos os pendentes" com um estilo/máscara escolhido.
- Regras automáticas por categoria (ex.: todo app de "Redes sociais" ganha a mesma paleta de fundo).
- Sincronização: ao instalar um app novo, ele entra automaticamente na aba "Pendentes" com notificação opcional.

---

## 2. Criador de Widgets

### 2.1 Objetivo
Um construtor visual de widgets nativo, onde o usuário monta o widget arrastando componentes num canvas, define tamanho, comportamento e vínculo de dados, e publica como widget real do Android (via `AppWidgetProvider`), sem escrever código.

### 2.2 Canvas e componentes disponíveis
- Grade de posicionamento baseada em células (1×1 até, por ex., 4×4), com snap automático.
- Componentes arrastáveis:
  - Texto (com fonte, tamanho, cor, peso, alinhamento)
  - Imagem/ícone (upload ou biblioteca)
  - Formas (retângulo, círculo, linha divisória)
  - Relógio (digital/analógico) com fonte customizável
  - Calendário/data
  - Indicador de bateria (estilizado, lendo o valor real do sistema)
  - Barra/anel de progresso (para bateria, passos, contagem regressiva)
  - Atalho de app (ícone + ação de abrir)
  - Espaço reservado de clima (placeholder até integração de API de clima)
- Cada componente tem painel de propriedades: posição, tamanho, cor, opacidade, sombra, raio de canto, efeito vidro/blur (glassmorphism).

### 2.3 Tamanhos responsivos
- Suporte às classes de tamanho padrão do Android (ex.: 2×2, 4×2, 4×4) com **preview automático em cada tamanho** — o usuário edita uma vez e vê como fica em todas as variações, podendo travar um layout específico por tamanho se quiser.

### 2.4 Vínculos de dados (bindings)
Lista de fontes de dados que um componente pode "ouvir": hora/data do sistema, nível de bateria, nome/ícone de um app escolhido, contagem de notificações (se permitido pelo Android), texto estático digitado pelo usuário, e — como evolução — clima (via API externa) e eventos de calendário.

### 2.5 Modelos prontos e comunidade
- Galeria de templates (minimalista, vidro fosco, neon, pastel etc.) para começar a partir de algo pronto.
- Botão "usar como base" que clona o template para edição livre.
- Publicação do widget criado na comunidade Homely, com preview, categoria e contador de downloads/curtidas (reaproveitando o sistema social já existente no app).

### 2.6 Posicionamento livre (evolução)
🟢 MVP: grade fixa por células, ordenação por camadas (frente/trás).
🔵 Perfeita: arrastar livremente em qualquer posição/ângulo dentro do widget, com guias de alinhamento estilo Canva (snap a outros elementos, distribuição uniforme, alinhar ao centro).

---

## 3. Compositor de Temas

### 3.1 Paleta de cores
- Extração automática de paleta a partir do papel de parede escolhido (cores dominantes, cor de destaque sugerida).
- Editor manual de paleta em HEX: cor primária, secundária, fundo, superfície, texto, com pré-visualização instantânea em toda a interface do Homely Home.
- Geração automática de variante clara e escura a partir da mesma paleta base.

### 3.2 Tipografia
- Seleção de fonte para o relógio e para os textos gerais do tema (biblioteca de fontes + upload de fonte própria como evolução).
- Peso, espaçamento e tamanho ajustáveis.

### 3.3 Forma dos ícones
- O tema herda a máscara/estilo definidos no Criador de Ícones (seção 1), aplicando-a de forma consistente a todos os ícones que ainda não têm edição individual.

### 3.4 Papel de parede
- Estático (upload de imagem) ou MP4 (com confirmação nativa do sistema, conforme já implementado).
- Biblioteca de wallpapers procedurais originais do Homely.

### 3.5 Empacotamento `.homely`
- Um tema completo (paleta + ícones + widgets + wallpaper + tipografia) é salvo como um único pacote `.homely`, importável/exportável, versionado (rascunho → publicado).

### 3.6 Prévia em dispositivo (mockup)
- Tela de prévia mostrando o tema aplicado dentro de uma moldura de celular, incluindo a simulação da tela inicial com os widgets e ícones já posicionados, antes de aplicar de verdade.

---

## 4. Interface e navegação do Studio (estilo Canva)

### 4.1 Estrutura de telas
```
Studio
 ├─ Início do Studio (meus temas, rascunhos, templates em destaque)
 ├─ Editor de Ícones
 │   ├─ Lista de apps (Todos / Modificados / Pendentes / Por pacote)
 │   └─ Canvas de edição de ícone
 ├─ Editor de Widgets
 │   ├─ Meus widgets
 │   └─ Canvas de edição de widget
 ├─ Compositor de Tema
 │   ├─ Paleta
 │   ├─ Tipografia
 │   ├─ Wallpaper
 │   └─ Prévia (mockup)
 └─ Publicar / Exportar (.homely)
```

### 4.2 Painel de camadas
Lista vertical reordenável (arrastar para mudar ordem de sobreposição), com ícone de visibilidade (olho) e bloqueio por camada, igual ao padrão de editores tipo Canva/Figma.

### 4.3 Barra de ferramentas contextual
Muda conforme o que está selecionado: selecionar imagem → aparecem recorte/filtros; selecionar texto → aparecem fonte/tamanho/cor; selecionar forma → aparecem preenchimento/borda.

### 4.4 Interações essenciais do editor
- Desfazer/refazer (undo/redo) com histórico persistente durante a sessão.
- Zoom e pan no canvas (pinça e botões +/-).
- Guias de alinhamento automáticas (snap ao centro, às bordas, a outros elementos).
- Atalho de duplicar elemento/camada.
- Estado de "salvamento automático" do rascunho, com indicador visual (salvo/salvando).

### 4.5 Estados vazios e onboarding
- Primeira vez no Studio: tour rápido de 3–4 telas mostrando ícones, widgets e temas.
- Lista vazia de "Meus temas": call-to-action para criar do zero ou partir de um template.
- Empty state amigável (ilustração + texto curto) em qualquer lista sem conteúdo, nunca uma tela em branco.

---

## 5. Limites técnicos reais do Android (para não prometer o impossível)

| Elemento | Pode ser mudado por um app comum (sem root)? | Observação |
|---|---|---|
| Ícone de apps na tela do Homely Home | ✅ Sim | Via launcher próprio, com o app escolhido como padrão |
| Wallpaper estático/animado | ✅ Sim | APIs padrão do Android (`WallpaperManager`) |
| Atalhos e widgets do Homely na própria tela inicial | ✅ Sim | Desde que o Homely seja o launcher ativo |
| Widgets nativos (`AppWidgetProvider`) | ✅ Sim | Funcionam em qualquer launcher, não só no Homely |
| Ícones de apps **fora** do Homely Home (outro launcher, gaveta de apps de terceiros) | ⚠️ Parcial | Só apps que suportam icon packs de terceiros conseguem ler os ícones do Homely |
| Barra de status do sistema (bateria, wi-fi, sinal, relógio) | ❌ Não | Pertence ao System UI; requer root/Substratum ou API própria do fabricante |
| Ícones de notificação de outros apps | ❌ Não | Controlados pelo próprio app/sistema |
| Tela de bloqueio nativa | ⚠️ Parcial | Só o papel de parede; o layout do lockscreen é do sistema |

Essa tabela deve acompanhar qualquer prévia do Studio que sugira mudança de ícones de sistema, para alinhar expectativa do usuário com a realidade da plataforma.

---

## 6. Checklist de aceite

### 🟢 Versão aceitável (MVP)
- [ ] Lista de apps com abas Todos/Modificados/Pendentes
- [ ] Editor de ícone com upload de imagem própria, máscara, cor de fundo, sombra
- [ ] Camadas adaptativas (fundo + primeiro plano) funcionando no Homely Home
- [ ] Aplicação em massa de máscara/estilo a ícones pendentes
- [ ] Criador de widget com pelo menos: texto, imagem, relógio, bateria, atalho de app
- [ ] Grade de tamanhos padrão (2×2, 4×2, 4×4) com preview
- [ ] Paleta de cores editável + extração automática do wallpaper
- [ ] Export/import `.homely`
- [ ] Prévia em mockup antes de aplicar
- [ ] Aviso claro sobre o que não pode ser alterado (barra de status do sistema)

### 🔵 Versão perfeita (visão avançada)
- [ ] Remoção de fundo assistida por IA no upload de ícone
- [ ] Máscara customizada via SVG do próprio usuário
- [ ] Posicionamento 100% livre dentro do widget, com guias de alinhamento
- [ ] Bindings de dados externos (clima, calendário real)
- [ ] Marketplace com curadoria, categorias e busca avançada de temas/ícones/widgets da comunidade
- [ ] Histórico completo de versões por ícone/widget/tema, com restauração
- [ ] Modo avançado opcional (root/Substratum) para quem quiser ir além dos limites padrão do Android
- [ ] Fonte customizada enviada pelo usuário
- [ ] Colaboração: convidar alguém para editar um tema em conjunto

---

## 7. Roadmap sugerido

1. **Fase 1 — Fundação:** lista de apps com status, editor básico de ícone (upload + máscara + cor), export/import `.homely` simples.
2. **Fase 2 — Widgets:** canvas de widget com componentes essenciais e grade de tamanhos.
3. **Fase 3 — Tema completo:** paleta automática, tipografia, prévia em mockup.
4. **Fase 4 — Comunidade:** publicação, busca, curadoria de temas/ícones/widgets de terceiros.
5. **Fase 5 — Avançado:** posicionamento livre, remoção de fundo por IA, bindings externos, modo root opcional.

---

*Este documento serve como especificação funcional e pode ser usado diretamente como prompt para uma IA de desenvolvimento (ex.: Claude Code) implementar o módulo Homely Studio por etapas, fase a fase.*