package io.github.nevaseme.plandmk1.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable fun AddressSearchField(value:String,onValueChange:(String)->Unit,onSearch:()->Unit,modifier:Modifier=Modifier,label:String="住所・建物名を検索",enabled:Boolean=true) {
 val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
 OutlinedTextField(value,{onValueChange(it.replace("\n"," ").replace("\r"," "))},label={Text(label)},singleLine=true,enabled=enabled,modifier=modifier,
  keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide();focus.clearFocus();onSearch()}),
  trailingIcon={if(value.isNotEmpty())IconButton(onClick={onValueChange("")},modifier=Modifier.size(48.dp)){PlanIcon("close","入力を消す")}})
}
