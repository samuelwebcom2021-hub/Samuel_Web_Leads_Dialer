package com.tuempresa.autodialer.ui.call.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DtmfKeypad(
    onDigitClick: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val digits = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#')
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        digits.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { digit ->
                    OutlinedButton(
                        onClick = { onDigitClick(digit) },
                        modifier = Modifier.size(72.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(text = digit.toString(), fontSize = 24.sp)
                    }
                }
            }
        }
    }
}
