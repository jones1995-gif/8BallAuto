# Pool Aim Auto 0.7 (beta de testes)

**Ferramenta independente para Android; não modifica o 8 Ball Pool.**

## Mudanças reais em relação à versão 0.6.2

1. Nova estimativa de colisão em `CollisionPhysics.java`: se a imagem permitir reconhecer uma bola-alvo na extremidade da guia nativa, calcula o contato entre dois discos de mesmo raio e estima as direções da bola-alvo e da bola branca. Não depende exclusivamente da detecção de duas linhas pequenas no jogo.
2. Se as duas linhas nativas forem identificadas **e** concordarem com a bola encontrada, prioriza as linhas lidas da tela. Caso contrário mostra `Colisão estimada` no menu para não confundir uma simulação com leitura real.
3. Seleção da bola branca aprimorada no `AimDetector`: exige circularidade também na borda, para não preferir letras brancas sobrepostas à mesa.
4. Filtro de candidatos próximos ao final da guia. Evita falsear colisões com texto, cursor ou elementos de interface. É conservador demais perto das bordas: bolas coladas à tabela podem não ser reconhecidas.
5. Traçado independente `RailTrace.java`, que calcula rebatidas para o **centro da bola** (com compensação do raio), trata aberturas aproximadas das seis caçapas e impede projetar uma rebatida depois de um buraco.
6. Correção de alinhamento da captura: descarta colunas extras que o `ImageReader` eventualmente adiciona no `rowStride` antes de reduzir e processar a imagem. É relevante para aparelhos que fornecem imagens com largura interna maior que a da tela.
7. Mantém a bola 8 flutuante, o modo de captura autorizado, projeção fina branca e ocultação enquanto a guia de mira não está presente / movimento reconhecido.

## Limitações conhecidas

- É uma **beta experimental**, não foi validada num telefone Android nem contra todas as mesas do jogo. Não há garantia de detectar cada colisão, cada tabela ou cada caçapa.
- Trajetórias calculadas assumem discos rígidos iguais, impacto ideal sem efeito, e reflexão especular nas tabelas. Força real, atrito, efeito, colisões subsequentes e diferenças entre mesas alteram as trajetórias. A posição final da bola **não** pode ser determinada com exatidão.
- Guias extremamente apagadas, alvos muito próximos das bordas, iluminação diferente, proporções/telas incomuns e sobreposições podem causar falhas.
- Não bloqueia nem modifica as proteções do jogo; o uso de qualquer auxiliar deve respeitar as regras do próprio jogo.

## Verificações executadas

- 32 testes de unidade Java locais passaram em 19/10/2026 (incluindo geometria de colisão, reconhecimento de círculos sintéticos, rebatidas, estados de movimento e regressões existentes).
- Testado offline com 17 capturas do vídeo fornecido pelo usuário (uma por segundo): 6 quadros sem colisão e 11 com bola-alvo reconhecida. A bola branca acompanha a posição da mira nos quadros analisados. Isso verifica o **detector em imagens fixas**, não demonstra que a sobreposição funciona continuamente em um celular.
- **Não foi possível compilar o APK Android no ambiente local**, porque não há Android SDK ou Gradle e não há rede para instalá-los. O GitHub Actions compilará o APK; a compilação pode revelar outros erros.

## Gerar o APK sem Android Studio (Windows Server)

1. Extraia o ZIP na máquina.
2. No repositório GitHub existente, envie **o conteúdo das pastas** (app, .github, arquivos .gradle etc.) para a raiz, substituindo a versão 0.6.2. Faça commit.
3. Em GitHub > Actions abra **Build Pool Aim Auto Beta 0.7 APK**. Se o fluxo não iniciar, pressione `Run workflow`.
4. Se terminar verde, abra `Summary` > `Artifacts` > `pool-aim-auto-v07-debug-apk`, extraia o ZIP de artefato e instale `app-debug.apk` no Android.
5. Ative a sobreposição, autorize a captura da tela inteira e teste mirando primeiro fora da bola, depois numa bola e finalmente perto das tabelas.

### Diagnóstico

Abra o menu da bola 8: **Mira detectada** indica leitura da guia nativa. **Colisão estimada** significa física calculada sobre uma bola detectada. **Colisão lida** significa duas linhas nativas aceitas pelo detector. As métricas `Imagens`, `guia`, `impactos`, `duplos`, `Física` e `linhas lidas` são cumulativas; não são taxas de acerto verificadas. O botão **Teste da linha branca** verifica se o overlay desenha mesmo quando o detector falha.
