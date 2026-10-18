# Pool Aim Auto Beta 0.2 — projeto de teste Android

Protótipo experimental de **detecção automática de linha clara** e projeção de rebatidas nas tabelas. Criado como nova versão para um **único teste no celular**. Não usa marcação manual de bolas nem lê memória ou modifica arquivos do 8 Ball Pool.

## O que existe nesta versão

- **Ícone e bolinha 8**: ícone na tela inicial e botão flutuante arrastável.
- **Menu minimizável** ao tocar na bolinha; acesso a ativar/desativar tabelas e parar captura.
- **Camada desenhada sem toques** (`FLAG_NOT_TOUCHABLE`) e com opacidade de janela inferior ao limite de bloqueio de toques do Android 12+.
- **Permissão de captura de tela** pelo `MediaProjection` (o Android mostra uma janela para pedir autorização).
- **Análise experimental automática**: localiza uma grande área colorida, identifica possíveis segmentos claros e desenha prolongamento até a tabela e duas rebatidas aproximadas. Atualiza periodicamente, em geral a cada ~650 ms.
- **Adaptação de captura a mudanças de tamanho de conteúdo** usando `VirtualDisplay.resize` (Android recente).
- Workflow do GitHub Actions para gerar o APK com Java 17, Gradle e Android SDK 35.

## Limitações reais

- O detector ainda **não foi testado num dispositivo Android real**; o teste incluído cobre apenas imagem sintética.
- Pode detectar elementos brancos da interface, reflexos, textos, bolas ou a linha errada. A identificação da mesa e sentido do taco pode falhar conforme cor, resolução e interface.
- As rebatidas são estimativas geométricas simples: não calculam as colisões precisas, força da tacada, efeito nem o ponto real onde cada bola para.
- Por regras de privacidade do Android, a captura de tela exige autorização do usuário. O jogo pode limitar sobreposições ou captura de tela; não há mecanismo para contornar proteções.
- Caso o jogo fique sem resposta, force a parada deste aplicativo pelas Configurações do Android. O painel minimizado deixa somente a bolinha arrastável.
- A imagem da bola 8 não contém fontes nem imagens de terceiros incorporadas.

## Compilar sem Android Studio (Windows Server 2012 R2)

**Para evitar misturar a versão antiga e a nova, use um repositório NOVO**, por exemplo `8BallAuto`. Não sobrescreva parte dos arquivos da versão antiga, pois ela contém outras classes Java e pode causar falha na compilação.

1. Extraia o ZIP e abra a pasta `pool-aim-auto-v02`.
2. No GitHub, crie um repositório **vazio** `8BallAuto`, sem adicionar README, .gitignore ou licença.
3. Na aba Code use **uploading an existing file**, arraste **todo o conteúdo interno** da pasta extraída, incluindo `.github` e `app`, e confirme em **Commit changes**. Não envie apenas o ZIP.
4. Abra **Actions → Build Pool Aim Auto Beta APK** e aguarde. Caso não inicie automaticamente, use **Run workflow**.
5. No job concluído com sucesso, abra **Summary → Artifacts → pool-aim-auto-beta-apk**.
6. Extraia o ZIP do artifact. Dentro estará `app-debug.apk`. Transfira para o Android e instale.
7. **Pare ou desinstale a versão antiga Pool Aim Overlay** antes de usar esta, para não deixar duas sobreposições ativas.
8. Para o primeiro teste, abra o 8 Ball Pool, volte para o aplicativo Pool Aim Auto Beta, autorize a sobreposição e a captura da tela. Se o jogo alterar orientação ou resolução, pode ser necessário reiniciar a captura com o jogo já em modo paisagem.
9. Teste se a bolinha 8 abre e fecha o menu e se você consegue tocar no jogo com o menu recolhido. Em seguida veja se as linhas são detectadas e acompanham a mira.

**Este ZIP contém código-fonte e não um APK previamente compilado.** O APK só existe se a execução do GitHub Actions terminar com sucesso. Não há garantia de que a detecção funcionará até testá-la no seu celular.

## Organização

- `app/src/main/java/br/com/poolaim/MainActivity.java`: permissões e tela inicial.
- `app/src/main/java/br/com/poolaim/AutoOverlayService.java`: bolinha flutuante e captura da tela.
- `app/src/main/java/br/com/poolaim/AimDetector.java`: algoritmo puro Java de reconhecimento visual.
- `app/src/main/java/br/com/poolaim/TrajectoryView.java`: linhas e reflexões desenhadas no canvas.
- `app/src/main/res/drawable-nodpi/bola_8.png`: imagem do ícone.
- `.github/workflows/android-apk.yml`: compilação em nuvem.
