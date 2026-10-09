// The "Are you sure you want to remove this event?" dialog, as the portal renders it (seen 2026-10-08).
function deleteDialogHtml() {
  return `
  <div role="dialog" aria-modal="true" id="dlg" style="display:none; position:fixed; top:10%; left:20%; background:#fff; padding:20px; border:1px solid #999">
    <div class="popup-content">
      <div class="bit-popup-title">Are you sure you want to remove this event?</div>
      <div class="delete-reason-options">
        <div class="field"><input type="text" name="reason" value="" readonly><label>Please tell us why*</label>
          <button type="button" aria-label="Toggle dropdown" id="reasonToggle"><svg width="12" height="12"></svg></button></div>
        <ul id="reasons" style="display:none">
          <li value="CANCELED"><button type="button">This event was canceled</button></li>
          <li value="POSTPONED"><button type="button">This event was postponed</button></li>
          <li value="OTHER"><button type="button">Other</button></li>
        </ul>
      </div>
      <textarea placeholder="Additional details" id="details"></textarea>
      <div><button type="button" id="dlgCancel">Cancel</button><button type="button" id="dlgDelete">Delete</button></div>
    </div>
  </div>`;
}

function wireDeleteDialog(eventIdOf) {
  const dlg = document.getElementById('dlg');
  document.getElementById('reasonToggle').onclick = () => document.getElementById('reasons').style.display = 'block';
  document.querySelectorAll('#reasons li').forEach(li => li.querySelector('button').onclick = () => {
    dlg.querySelector('input[name=reason]').value = li.getAttribute('value');
    document.getElementById('reasons').style.display = 'none';
  });
  document.getElementById('dlgCancel').onclick = () => dlg.style.display = 'none';
  document.getElementById('dlgDelete').onclick = () => fetch('/api/managed-actors/1/events/' + eventIdOf(), {
    method: 'PATCH',
    body: JSON.stringify({status: 'DELETED', reason: dlg.querySelector('input[name=reason]').value,
                          detail: document.getElementById('details').value})
  }).then(() => dlg.style.display = 'none');
  return () => dlg.style.display = 'block';
}
