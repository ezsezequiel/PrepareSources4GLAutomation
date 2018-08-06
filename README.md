# PrepareSources4GLAutomation
Preparação de fontes 4GL para automação de testes em telas do tipo PER

Com o propósito de não utilizar mais ferramentas de gravação de testes automatizado com mapeamente de tela, este programa 
auxiliar na preparação de um fonte 4GL (Informix) para automação.

Considerando os tipos de INPUT's abaixo:

  - INPUT

  - INPUT BY NAME

  - DISPLAY

  - DISPLAY ARRAY
  
  
 Para os tipos INPUT e INPUT BY NAME, a seguinte regra é aplicada:
 
 Quando o INPUT existe FIELD, é incluída a chamada da function <program_name>_save_cur_field_name(<field_name>) no final do bloco BEFORE FIELD
 
 Quando o INPUT não existe FIELD, é incluída a chamada da function <program_name>_save_cur_field_name(<field_name>) no final do bloco BEFORE INPUT
 
 Quando os blocos BEFORE INPUT ou BEFORE FIELD não existem, os mesmos são criados automaticamentes.
 
 
    INPUT l_tecla WITHOUT DEFAULTS FROM tecla
    
      BEFORE FIELD campo1
          CALL <program_name>_save_cur_field_name('campo1')
      
      AFTER FIELD campo1
          CALL CONOUT('CAMPO1')
          
      BEFORE FIELD campo2
          CALL <program_name>_save_cur_field_name('campo1')
      
      AFTER FIELD campo2
          CALL CONOUT('CAMPO2')
    
      ON KEY(control-y)
          CALL <program_name>_show_input_name('')
      
      ON KEY('SCAPE')
      
      ON KEY('RETURN')
    
      END INPUT
      
      
      
      
Observação:
 
Para os casos de telas desktop (não coletores) a seguinte function deve ser criada no fonte e incluída a chamada da mesma nos blocos de BEFORE INPUT ou BEFORE DISPLAY, a fim de não confundir os usuários.
 
 FUNCTION <program_name>_key_automation_disable()
 
    IF LOG_qaModeEnabled() THEN
       CALL fgl_key_label('control-y',NULL)
    END IF
 
 END FUNCTION
 
